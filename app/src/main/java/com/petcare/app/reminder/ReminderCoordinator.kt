package com.petcare.app.reminder

import com.petcare.app.data.preferences.UserPreferences
import com.petcare.app.data.repository.AuthRepository
import com.petcare.app.data.repository.TaskRepository
import com.petcare.app.data.repository.SmsReminderRepository
import com.petcare.app.domain.TaskSchedule
import com.petcare.app.model.CareTask
import com.petcare.app.model.time
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Keeps the OS alarm set equal to the signed-in owner's Room tasks. A single
 * coordinator is started by Application and also refreshed by foreground and
 * system lifecycle events.
 */
class ReminderCoordinator(
    private val authRepository: AuthRepository,
    private val taskRepository: TaskRepository,
    private val scheduler: ReminderAlarmScheduler,
    private val smsRepository: SmsReminderRepository? = null,
    private val smsSender: AutomaticSmsSender? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    /** Testable boundary immediately before the shared authorization gate. */
    private val beforeSmsAuthorization: suspend () -> Unit = {},
    /** Account-scoped master switch; null (tests) means always on. */
    private val preferences: UserPreferences? = null
) {
    private data class OwnerTasks(val ownerId: Long, val tasks: List<CareTask>)

    private var started = false
    private val reconcileMutex = Mutex()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            authRepository.currentUser
                .flatMapLatest { user ->
                    if (user == null) {
                        flowOf<OwnerTasks?>(null)
                    } else {
                        combine(
                            taskRepository.observeTasks(user.id),
                            remindersEnabled(user.id)
                        ) { tasks, enabled -> OwnerTasks(user.id, if (enabled) tasks else emptyList()) }
                    }
                }
                .collectLatest { ownerAndTasks ->
                    reconcileMutex.withLock {
                        if (ownerAndTasks == null) {
                            scheduler.cancelAll()
                        } else {
                            reconcile(
                                ownerId = ownerAndTasks.ownerId,
                                tasks = ownerAndTasks.tasks,
                                preservePending = true
                            )
                        }
                    }
                }
        }
    }

    /** Reconciles immediately, used after boot/time changes and on foreground. */
    suspend fun refreshNow(forceReschedule: Boolean = false) {
        reconcileMutex.withLock {
            val user = authRepository.currentUser.first()
            if (user == null) {
                scheduler.cancelAll()
            } else {
                reconcile(
                    ownerId = user.id,
                    tasks = if (remindersEnabled(user.id).first()) taskRepository.observeTasks(user.id).first() else emptyList(),
                    preservePending = !forceReschedule,
                    forceReschedule = forceReschedule
                )
            }
        }
    }

    fun refresh() {
        scope.launch { refreshNow() }
    }

    /**
     * Handles one alarm while re-checking ownership and the current task row.
     * This prevents a stale alarm from leaking task content after logout or an
     * account switch.
     */
    suspend fun handleAlarm(
        ownerId: Long,
        taskId: Long,
        dueEpochDay: Long,
        triggerAtMillis: Long
    ) {
        reconcileMutex.withLock {
            val user = authRepository.currentUser.first()
            val key = ReminderAlarmKey(ownerId, taskId)
            if (user?.id != ownerId) {
                scheduler.cancel(key)
                return
            }

            if (!remindersEnabled(ownerId).first()) {
                // The master switch was turned off after this alarm was armed.
                scheduler.cancel(key)
                return
            }
            val tasks = taskRepository.observeTasks(ownerId).first()
            val task = tasks.firstOrNull { it.id == taskId }
            if (task == null || (!task.reminderEnabled && !task.smsReminderEnabled)) {
                scheduler.cancel(key)
                return
            }

            val dueDate = runCatching { LocalDate.ofEpochDay(dueEpochDay) }.getOrNull()
            if (dueDate == null || !TaskSchedule.occursOn(task, dueDate)) {
                reconcile(ownerId, tasks, preservePending = true)
                return
            }

            val zone = ZoneId.systemDefault()
            val expectedAt = dueDate.atTime(task.time).atZone(zone)
            val now = ZonedDateTime.now(zone)
            // An edit can race an already-delivered PendingIntent. If the new
            // time is still in the future, do not notify early.
            if (now.isBefore(expectedAt) &&
                kotlin.math.abs(expectedAt.toInstant().toEpochMilli() - triggerAtMillis) >
                STALE_ALARM_TOLERANCE_MILLIS
            ) {
                reconcile(ownerId, tasks, preservePending = true)
                return
            }

            if (TaskSchedule.isCompletedOn(task, dueDate)) {
                // The observer may already have scheduled the next occurrence.
                // A stale delivery must not cancel that newer alarm.
                reconcile(ownerId, tasks, preservePending = true)
                return
            }

            // The SMS is attempted first so its outcome (sent, blocked, no SIM,
            // failed) is reported in the notification itself and never fails
            // silently.
            val smsLine = try {
                submitAutomaticSmsIfStillAuthorized(ownerId, task, dueDate)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A storage or telephony failure must never suppress the
                // notification or leave the alarm unretired.
                "SMS not sent — ${e.message ?: "unexpected error"}"
            }
            if (task.reminderEnabled &&
                ReminderNotification.postTaskNotification(schedulerContext(), task, smsLine)
            ) {
                scheduler.markNotificationPosted(key, task)
            }

            // A one-off is removed after its notification, but its notification
            // remains visible until the user dismisses it or signs out.
            val next = TaskSchedule.nextReminderAt(task, now)
            if (next == null) {
                scheduler.retire(key)
            } else {
                scheduler.schedule(ownerId, task, next)
            }
        }
    }

    private suspend fun reconcile(
        ownerId: Long,
        tasks: List<CareTask>,
        preservePending: Boolean,
        forceReschedule: Boolean = false
    ) {
        val now = ZonedDateTime.now()
        val nowMillis = now.toInstant().toEpochMilli()
        val tracked = scheduler.tracked()
        val desired = tasks
            .filter { it.reminderEnabled || it.smsReminderEnabled }
            .associateBy { ReminderAlarmKey(ownerId, it.id) }

        tracked.keys
            .filter { it.ownerId != ownerId || it !in desired.keys }
            .forEach(scheduler::cancel)

        scheduler.postedNotifications()
            .filter { key ->
                val task = desired[key]
                key.ownerId != ownerId ||
                    task == null ||
                    (scheduler.notificationSignature(key) != null &&
                        scheduler.notificationSignature(key) != scheduler.scheduleSignature(task))
            }
            .forEach(scheduler::dismissPostedNotification)

        desired.forEach { (key, task) ->
            val trackedAt = tracked[key]
            val oldSignature = scheduler.scheduleSignature(key)
            val newSignature = scheduler.scheduleSignature(task)
            val sameSchedule = trackedAt != null && oldSignature == newSignature
            // Alarms saved by older versions were all inexact, so a missing
            // mode safely means false until the next reconciliation writes it.
            val sameAlarmMode =
                (scheduler.exactAlarm(key) ?: false) == scheduler.currentExactAlarmAccess()
            val dueEpochDay = scheduler.dueEpochDay(key)

            if (trackedAt != null && !sameSchedule) {
                // A changed task must supersede the old PendingIntent and any
                // notification that it may already have delivered.
                scheduler.cancel(key)
            } else if (trackedAt != null && !sameAlarmMode) {
                // Android can cancel exact alarms when access is revoked. Keep
                // an already-delivered notification, but replace the OS alarm.
                scheduler.retire(key)
            }
            if (sameSchedule && sameAlarmMode && preservePending) return@forEach

            val next = if (
                (forceReschedule || !sameAlarmMode) &&
                sameSchedule &&
                trackedAt <= nowMillis &&
                dueEpochDay != null
            ) {
                // A reboot, time change, or exact-alarm revocation can erase an
                // overdue OS alarm. Re-arm that local occurrence immediately
                // rather than silently skipping a one-off reminder.
                runCatching {
                    LocalDate.ofEpochDay(dueEpochDay)
                        .atTime(task.time)
                        .atZone(now.zone)
                }.getOrNull()
            } else {
                TaskSchedule.nextReminderAt(task, now)
            }
            // A delivered one-off has no next alarm, but its notification must
            // survive app foregrounding. Stale/completed/deleted notifications
            // are dismissed by the signature/ownership checks above.
            if (next == null) scheduler.retire(key)
            else scheduler.schedule(ownerId, task, next)
        }
    }

    private fun schedulerContext() = scheduler.notificationContext

    private fun remindersEnabled(ownerId: Long): Flow<Boolean> =
        preferences?.remindersEnabled(ownerId) ?: flowOf(true)

    /** Without a preference store (some tests) consent is treated as given. */
    private fun smsConsent(ownerId: Long): Flow<Boolean> =
        preferences?.smsConsent(ownerId) ?: flowOf(true)

    /**
     * This deliberately claims before touching the modem. Any post-claim race,
     * process death, or carrier ambiguity is a missed SMS rather than a second
     * potentially charged message.
     */
    private suspend fun submitAutomaticSmsIfStillAuthorized(
        ownerId: Long,
        scheduledTask: CareTask,
        dueDate: LocalDate
    ): String? {
        val repository = smsRepository ?: return null
        val sender = smsSender ?: return null
        if (!scheduledTask.smsReminderEnabled || scheduledTask.smsRecipient.isBlank()) return null
        beforeSmsAuthorization()
        return authRepository.smsAuthorizationGate.withLock {
            // Claim first, even if the OS revoked SMS since scheduling. This
            // writes an honest unavailable/cancelled outcome and prevents an
            // automatic retry of an ambiguous due occurrence.
            val occurrenceId = repository.claim(ownerId, scheduledTask.id, dueDate.toEpochDay())
            if (occurrenceId <= 0L) {
                // A repeated alarm for an already-claimed occurrence must not
                // resend, and must not replace the earlier outcome with silence.
                return@withLock earlierOutcomeLine(repository, scheduledTask)
            }

            // Re-read through the DAO after the irreversible claim while task
            // writes and session changes share this authorization gate.
            val user = authRepository.currentUser.first()
            val current = repository.findOwnedTask(scheduledTask.id, ownerId)
            // The account's reminders switch and its one-time SMS consent are
            // re-read here, inside the gate, so a switch flipped after the
            // early check above still stops the submission.
            if (!remindersEnabled(ownerId).first()) {
                repository.updateStatus(occurrenceId, "CANCELLED", "Reminders were turned off for this account before SMS submission.")
                return@withLock null
            }
            if (!smsConsent(ownerId).first()) {
                repository.updateStatus(occurrenceId, "CANCELLED", "This account has not agreed to automatic SMS.")
                return@withLock "SMS not sent — agree to automatic SMS in Settings"
            }
            if (user?.id != ownerId || current == null ||
                !current.smsReminderEnabled || current.smsRecipient != scheduledTask.smsRecipient ||
                current.timeMinutes != scheduledTask.timeMinutes ||
                current.frequency != scheduledTask.frequency ||
                current.startDateEpochDay != scheduledTask.startDateEpochDay ||
                !TaskSchedule.occursOn(current, dueDate) || TaskSchedule.isCompletedOn(current, dueDate)
            ) {
                repository.updateStatus(occurrenceId, "CANCELLED", "Task, owner, schedule, or consent changed before SMS submission.")
                return@withLock null
            }
            val availability = sender.availability()
            if (availability !is SmsAvailability.Ready) {
                repository.updateStatus(occurrenceId, "UNAVAILABLE", availabilityMessage(availability))
                return@withLock when (availability) {
                    SmsAvailability.PermissionMissing -> SMS_NOT_SENT_LINE
                    SmsAvailability.DefaultSimUnavailable -> "SMS not sent — no SIM is ready for SMS"
                    else -> "SMS not sent — this phone has no SMS service"
                }
            }
            val pet = repository.findOwnedPet(current.petId, ownerId)
            if (pet == null) {
                repository.updateStatus(occurrenceId, "CANCELLED", "The pet for this task no longer exists.")
                return@withLock null
            }
            val message = TaskSmsTemplate.build(current, pet, dueDate, LocalDate.now())
            sender.submit(current, message, occurrenceId) { parts ->
                repository.markSubmitted(occurrenceId, parts)
            }.fold(
                // The modem's answer arrives later through SmsSentStatusReceiver,
                // which re-posts the notification with "sent" or the failure.
                onSuccess = { "SMS sending to ${current.smsRecipient}" },
                onFailure = { error ->
                    repository.updateStatus(
                        occurrenceId, "FAILED",
                        "SMS could not be submitted: ${error.message ?: "unknown error"}"
                    )
                    "SMS not sent — ${error.message ?: "unknown error"}"
                }
            )
        }
    }

    private suspend fun earlierOutcomeLine(repository: SmsReminderRepository, task: CareTask): String? {
        val status = repository.latestStatus(task.id) ?: return null
        return when (status.substringBefore(':')) {
            "SENT" -> "SMS sent to ${task.smsRecipient}"
            "SUBMITTED" -> "SMS sending to ${task.smsRecipient}"
            "FAILED", "UNAVAILABLE" -> "SMS not sent — see the task for details"
            else -> null
        }
    }

    companion object {
        private const val STALE_ALARM_TOLERANCE_MILLIS = 60_000L
        const val SMS_NOT_SENT_LINE = "SMS not sent — allow SMS in Settings"
    }
}