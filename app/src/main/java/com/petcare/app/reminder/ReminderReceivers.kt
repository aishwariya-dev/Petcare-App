package com.petcare.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.petcare.app.PetCareApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val REMINDER_TAG = "PetCareReminder"

/** Receiver work is asynchronous so Room and notification work do not block onReceive. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val app = context.applicationContext as? PetCareApp
        if (app == null) {
            pendingResult.finish()
            return
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val ownerId = intent.getLongExtra(EXTRA_OWNER_ID, -1L)
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
                val dueEpochDay = intent.getLongExtra(EXTRA_DUE_EPOCH_DAY, Long.MIN_VALUE)
                val triggerAt = intent.getLongExtra(EXTRA_TRIGGER_AT_MILLIS, 0L)
                if (ownerId > 0L && taskId > 0L && dueEpochDay != Long.MIN_VALUE) {
                    app.reminderCoordinator.handleAlarm(ownerId, taskId, dueEpochDay, triggerAt)
                }
            } catch (error: Throwable) {
                Log.e(REMINDER_TAG, "Unable to handle care reminder alarm", error)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/** Rebuilds reminder alarms after reboot, timezone/time changes, and updates. */
class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
            )
        ) return

        val pendingResult = goAsync()
        val app = context.applicationContext as? PetCareApp
        if (app == null) {
            pendingResult.finish()
            return
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                app.reminderCoordinator.refreshNow(forceReschedule = true)
            } catch (error: Throwable) {
                Log.e(REMINDER_TAG, "Unable to restore care reminder alarms", error)
            } finally {
                pendingResult.finish()
            }
        }
    }
}