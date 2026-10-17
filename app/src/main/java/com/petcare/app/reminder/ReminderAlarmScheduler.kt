package com.petcare.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.petcare.app.model.CareTask
import java.time.ZonedDateTime

/** Uses exact idle-tolerant alarms when Android grants that access, with an inexact fallback. */
class ReminderAlarmScheduler internal constructor(
    context: Context,
    private val setExactAlarm: (AlarmManager?, Long, PendingIntent) -> Unit
) {
    constructor(context: Context) : this(
        context,
        { alarmManager, triggerAtMillis, pendingIntent ->
            alarmManager?.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    )

    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)
    private val store = ReminderAlarmStore(appContext)

    internal val notificationContext: Context
        get() = appContext

    init {
        ReminderNotification.ensureChannel(appContext)
    }

    fun tracked(): Map<ReminderAlarmKey, Long> = store.all()

    fun schedule(ownerId: Long, task: CareTask, triggerAt: ZonedDateTime) {
        val key = ReminderAlarmKey(ownerId, task.id)
        val triggerAtMillis = triggerAt.toInstant().toEpochMilli()
            .coerceAtLeast(System.currentTimeMillis() + MINIMUM_DELAY_MILLIS)
        val pendingIntent = pendingIntent(key, task, triggerAt)
        var exactAlarm = canScheduleExactAlarms(appContext)
        try {
            if (exactAlarm) {
                setExactAlarm(alarmManager, triggerAtMillis, pendingIntent)
            } else {
                alarmManager?.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (error: SecurityException) {
            if (!exactAlarm) throw error
            // Access can be revoked between the check and AlarmManager call.
            exactAlarm = false
            alarmManager?.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
        store.put(
            key = key,
            triggerAtMillis = triggerAtMillis,
            dueEpochDay = triggerAt.toLocalDate().toEpochDay(),
            scheduleSignature = scheduleSignature(task),
            exactAlarm = exactAlarm
        )
    }

    /** Cancels an OS alarm and its already-posted notification. */
    fun cancel(key: ReminderAlarmKey) {
        alarmManager?.cancel(pendingIntent(key, null, null))
        store.remove(key)
        ReminderNotification.cancelTaskNotification(appContext, key.taskId)
        store.clearNotification(key)
    }

    /** Removes only the OS alarm, retaining a delivered notification. */
    fun retire(key: ReminderAlarmKey) {
        alarmManager?.cancel(pendingIntent(key, null, null))
        store.remove(key)
    }

    fun cancelAll() {
        tracked().keys.forEach(::cancel)
        postedNotifications().forEach(::dismissPostedNotification)
        store.clear()
    }

    fun dismissPostedNotification(key: ReminderAlarmKey) {
        ReminderNotification.cancelTaskNotification(appContext, key.taskId)
        store.clearNotification(key)
    }

    fun markNotificationPosted(key: ReminderAlarmKey, task: CareTask? = null) {
        store.markNotificationPosted(key, task?.let { scheduleSignature(it) })
    }

    fun postedNotifications(): Set<ReminderAlarmKey> = store.postedNotifications()

    fun notificationSignature(key: ReminderAlarmKey): String? = store.notificationSignature(key)

    fun dueEpochDay(key: ReminderAlarmKey): Long? = store.dueEpochDay(key)

    fun scheduleSignature(key: ReminderAlarmKey): String? = store.scheduleSignature(key)

    fun exactAlarm(key: ReminderAlarmKey): Boolean? = store.exactAlarm(key)

    fun currentExactAlarmAccess(): Boolean = canScheduleExactAlarms(appContext)

    fun scheduleSignature(task: CareTask): String = listOf(
        task.petId,
        task.name,
        task.category,
        task.frequency,
        task.startDateEpochDay,
        task.timeMinutes,
        task.reminderEnabled,
        task.smsReminderEnabled,
        task.smsRecipient,
        task.lastCompletedEpochDay,
        task.supplies,
        task.notes,
        task.imagePath
    ).joinToString("|")

    private fun pendingIntent(
        key: ReminderAlarmKey,
        task: CareTask?,
        triggerAt: ZonedDateTime?
    ): PendingIntent {
        val intent = Intent(appContext, ReminderAlarmReceiver::class.java).apply {
            putExtra(EXTRA_OWNER_ID, key.ownerId)
            putExtra(EXTRA_TASK_ID, key.taskId)
            data = Uri.parse("petcare://reminder/${key.ownerId}/${key.taskId}")
            if (task != null && triggerAt != null) {
                putExtra(EXTRA_DUE_EPOCH_DAY, triggerAt.toLocalDate().toEpochDay())
                putExtra(EXTRA_TRIGGER_AT_MILLIS, triggerAt.toInstant().toEpochMilli())
            }
        }
        return PendingIntent.getBroadcast(
            appContext,
            requestCode(key),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun requestCode(key: ReminderAlarmKey): Int =
        ((key.ownerId * 31L + key.taskId) xor (key.ownerId ushr 32) xor (key.taskId ushr 32))
            .toInt()
            .and(0x7fffffff)
            .coerceAtLeast(1)

    companion object {
        fun canScheduleExactAlarms(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

        private const val MINIMUM_DELAY_MILLIS = 1_000L
    }
}

internal fun openExactAlarmSettings(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

internal const val EXTRA_OWNER_ID = "com.petcare.app.reminder.owner_id"
internal const val EXTRA_TASK_ID = "com.petcare.app.reminder.task_id"
internal const val EXTRA_DUE_EPOCH_DAY = "com.petcare.app.reminder.due_epoch_day"
internal const val EXTRA_TRIGGER_AT_MILLIS = "com.petcare.app.reminder.trigger_at_millis"