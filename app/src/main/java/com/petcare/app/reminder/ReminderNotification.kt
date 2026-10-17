package com.petcare.app.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.petcare.app.MainActivity
import com.petcare.app.model.CareTask
import com.petcare.app.model.Pet
import java.time.LocalDate
import com.petcare.app.util.DateTimeFormats

data class ReminderNotificationContent(
    val title: String,
    val body: String,
    val time: String
)

data class TaskReminderPreview(
    val notification: ReminderNotificationContent,
    val localNotificationEnabled: Boolean,
    val sms: AutomaticSmsPayload?
)

/** Notification channel and content helpers. No notification is sent without a signed-in owner. */
object ReminderNotification {
    // v2 is intentional: Android does not raise an existing channel's importance.
    const val CHANNEL_ID = "care_reminders_v2"
    private const val CHANNEL_NAME = "Care reminders"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Reminders for scheduled pet-care tasks"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 120, 250)
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    fun hasRuntimePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    fun canPost(context: Context): Boolean {
        if (!hasRuntimePermission(context) ||
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) return false
        // App-level access is not enough: users can block this channel separately.
        return context.getSystemService(NotificationManager::class.java)
            ?.getNotificationChannel(CHANNEL_ID)
            ?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun postTaskNotification(context: Context, task: CareTask, extraLine: String? = null): Boolean {
        ensureChannel(context)
        val content = content(task)
        return post(
            context = context,
            notificationId = taskNotificationId(task.id),
            title = content.title,
            text = if (extraLine.isNullOrBlank()) content.body else "${content.body}\n$extraLine"
        )
    }

    /** Shared by the real notification and the save confirmation preview. */
    fun content(task: CareTask): ReminderNotificationContent {
        val time = DateTimeFormats.time(task.timeMinutes)
        return ReminderNotificationContent(
            title = "Care reminder",
            body = listOf(time, task.name, task.notes.trim())
                .filter { it.isNotBlank() }
                .joinToString(" — "),
            time = time
        )
    }

    fun preview(task: CareTask, pet: Pet, occurrence: LocalDate, today: LocalDate): TaskReminderPreview =
        TaskReminderPreview(
            notification = content(task),
            localNotificationEnabled = task.reminderEnabled,
            sms = automaticSmsPayload(task, pet, occurrence, today).takeIf { task.smsReminderEnabled }
        )

    fun cancelTaskNotification(context: Context, taskId: Long) {
        NotificationManagerCompat.from(context).cancel(taskNotificationId(taskId))
    }

    private fun post(context: Context, notificationId: Int, title: String, text: String): Boolean {
        if (!canPost(context)) return false
        val openApp = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        } catch (_: SecurityException) {
            // Permission can be revoked between the check and notify().
            false
        }
    }

    private fun taskNotificationId(taskId: Long): Int =
        (taskId xor (taskId ushr 32)).toInt().and(0x7fffffff).coerceAtLeast(1)
}