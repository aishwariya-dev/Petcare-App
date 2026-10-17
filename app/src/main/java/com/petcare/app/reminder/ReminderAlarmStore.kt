package com.petcare.app.reminder

import android.content.Context

/** Stable, private bookkeeping for alarms so edits and account changes can cancel them. */
data class ReminderAlarmKey(val ownerId: Long, val taskId: Long)

class ReminderAlarmStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "petcare_reminder_alarms",
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun all(): Map<ReminderAlarmKey, Long> = preferences.all.mapNotNull { (name, value) ->
        if (!name.startsWith(PREFIX) || value !is Number) return@mapNotNull null
        val parts = name.removePrefix(PREFIX).split('_', limit = 2)
        if (parts.size != 2) return@mapNotNull null
        val ownerId = parts[0].toLongOrNull() ?: return@mapNotNull null
        val taskId = parts[1].toLongOrNull() ?: return@mapNotNull null
        ReminderAlarmKey(ownerId, taskId) to value.toLong()
    }.toMap()

    @Synchronized
    fun put(key: ReminderAlarmKey, triggerAtMillis: Long) {
        put(key, triggerAtMillis, dueEpochDay = null, scheduleSignature = null)
    }

    @Synchronized
    fun put(
        key: ReminderAlarmKey,
        triggerAtMillis: Long,
        dueEpochDay: Long?,
        scheduleSignature: String?,
        exactAlarm: Boolean? = null
    ) {
        preferences.edit().apply {
            putLong(key.preferenceName(), triggerAtMillis)
            if (dueEpochDay == null) remove(key.dueDatePreferenceName())
            else putLong(key.dueDatePreferenceName(), dueEpochDay)
            if (scheduleSignature == null) remove(key.signaturePreferenceName())
            else putString(key.signaturePreferenceName(), scheduleSignature)
            if (exactAlarm == null) remove(key.exactAlarmPreferenceName())
            else putBoolean(key.exactAlarmPreferenceName(), exactAlarm)
        }.apply()
    }

    @Synchronized
    fun remove(key: ReminderAlarmKey) {
        preferences.edit()
            .remove(key.preferenceName())
            .remove(key.dueDatePreferenceName())
            .remove(key.signaturePreferenceName())
            .remove(key.exactAlarmPreferenceName())
            .apply()
    }

    @Synchronized
    fun clear() {
        preferences.edit().clear().apply()
    }

    @Synchronized
    fun dueEpochDay(key: ReminderAlarmKey): Long? =
        preferences.takeIf { it.contains(key.dueDatePreferenceName()) }
            ?.getLong(key.dueDatePreferenceName(), Long.MIN_VALUE)
            ?.takeUnless { it == Long.MIN_VALUE }

    @Synchronized
    fun scheduleSignature(key: ReminderAlarmKey): String? =
        preferences.getString(key.signaturePreferenceName(), null)

    @Synchronized
    fun exactAlarm(key: ReminderAlarmKey): Boolean? =
        preferences.takeIf { it.contains(key.exactAlarmPreferenceName()) }
            ?.getBoolean(key.exactAlarmPreferenceName(), false)

    @Synchronized
    fun markNotificationPosted(key: ReminderAlarmKey, scheduleSignature: String? = null) {
        preferences.edit().apply {
            putBoolean(key.notificationPreferenceName(), true)
            if (scheduleSignature == null) remove(key.notificationSignaturePreferenceName())
            else putString(key.notificationSignaturePreferenceName(), scheduleSignature)
        }.apply()
    }

    @Synchronized
    fun hasPostedNotification(key: ReminderAlarmKey): Boolean =
        preferences.getBoolean(key.notificationPreferenceName(), false)

    @Synchronized
    fun clearNotification(key: ReminderAlarmKey) {
        preferences.edit()
            .remove(key.notificationPreferenceName())
            .remove(key.notificationSignaturePreferenceName())
            .apply()
    }

    @Synchronized
    fun notificationSignature(key: ReminderAlarmKey): String? =
        preferences.getString(key.notificationSignaturePreferenceName(), null)

    @Synchronized
    fun postedNotifications(): Set<ReminderAlarmKey> = preferences.all.keys
        .filter { it.startsWith(NOTIFICATION_PREFIX) }
        .mapNotNull { name ->
            val parts = name.removePrefix(NOTIFICATION_PREFIX).split('_', limit = 2)
            if (parts.size != 2) return@mapNotNull null
            ReminderAlarmKey(parts[0].toLongOrNull() ?: return@mapNotNull null,
                parts[1].toLongOrNull() ?: return@mapNotNull null)
        }
        .toSet()

    private fun ReminderAlarmKey.preferenceName(): String = "$PREFIX${ownerId}_$taskId"
    private fun ReminderAlarmKey.dueDatePreferenceName(): String = "$DUE_PREFIX${ownerId}_$taskId"
    private fun ReminderAlarmKey.signaturePreferenceName(): String = "$SIGNATURE_PREFIX${ownerId}_$taskId"
    private fun ReminderAlarmKey.exactAlarmPreferenceName(): String = "$EXACT_PREFIX${ownerId}_$taskId"
    private fun ReminderAlarmKey.notificationPreferenceName(): String = "$NOTIFICATION_PREFIX${ownerId}_$taskId"
    private fun ReminderAlarmKey.notificationSignaturePreferenceName(): String =
        "$NOTIFICATION_SIGNATURE_PREFIX${ownerId}_$taskId"

    private companion object {
        const val PREFIX = "alarm_"
        const val DUE_PREFIX = "alarm_due_"
        const val SIGNATURE_PREFIX = "alarm_signature_"
        const val EXACT_PREFIX = "alarm_exact_"
        const val NOTIFICATION_PREFIX = "notification_"
        const val NOTIFICATION_SIGNATURE_PREFIX = "notification_signature_"
    }
}