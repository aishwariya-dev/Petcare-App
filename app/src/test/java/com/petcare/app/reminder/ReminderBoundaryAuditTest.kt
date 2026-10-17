package com.petcare.app.reminder

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.petcare.app.model.CareTask
import com.petcare.app.model.Frequency
import com.petcare.app.model.TaskCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Alarm persistence and receiver/AlarmManager boundaries. These tests use a
 * plain Application deliberately: coordinator work belongs to PetCareApp
 * lifecycle tests, not to this isolated Android boundary audit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReminderBoundaryAuditTest {
    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancelAll()
        NotificationManagerCompat.from(context).cancelAll()
        context.getSharedPreferences("petcare_reminder_alarms", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `store ignores malformed preference keys and round trips alarm metadata`() {
        val key = ReminderAlarmKey(ownerId = 11, taskId = 22)
        context.getSharedPreferences("petcare_reminder_alarms", Context.MODE_PRIVATE)
            .edit()
            .putLong("alarm_11_22", 1234L)
            .putLong("alarm_11_22_extra", 5678L)
            .putString("alarm_11_bad", "not-a-time")
            .putBoolean("alarm_bad_22", true)
            .putLong("notification_11_22_extra", 1L)
            .commit()
        val store = ReminderAlarmStore(context)

        assertEquals(mapOf(key to 1234L), store.all())

        store.put(
            key = key,
            triggerAtMillis = 9876L,
            dueEpochDay = 20_000L,
            scheduleSignature = "task-v1",
            exactAlarm = true
        )
        store.markNotificationPosted(key, scheduleSignature = "task-v1")

        assertEquals(mapOf(key to 9876L), store.all())
        assertEquals(20_000L, store.dueEpochDay(key))
        assertEquals("task-v1", store.scheduleSignature(key))
        assertEquals(true, store.exactAlarm(key))
        assertEquals(setOf(key), store.postedNotifications())
        assertEquals("task-v1", store.notificationSignature(key))

        // Removing an alarm intentionally retains its delivered notification;
        // ReminderAlarmScheduler.cancel() clears that separate state.
        store.remove(key)
        assertTrue(store.all().isEmpty())
        assertNull(store.dueEpochDay(key))
        assertNull(store.scheduleSignature(key))
        assertNull(store.exactAlarm(key))
        assertEquals(setOf(key), store.postedNotifications())

        store.clearNotification(key)
        assertTrue(store.postedNotifications().isEmpty())
    }

    @Test
    fun `scheduling the same key replaces the alarm PendingIntent and payload`() {
        val scheduler = ReminderAlarmScheduler(context)
        val first = ZonedDateTime.now().plusMinutes(10)
        val second = first.plusDays(1)
        val key = ReminderAlarmKey(ownerId = 7, taskId = 31)

        scheduler.schedule(7L, task(id = key.taskId), first)
        scheduler.schedule(7L, task(id = key.taskId, name = "Updated care"), second)

        assertEquals(1, scheduler.tracked().size)
        assertEquals(second.toInstant().toEpochMilli(), scheduler.tracked().getValue(key))
        assertEquals("Updated care", scheduler.scheduleSignature(key)!!.split('|')[1])

        val scheduled = shadowOf(alarmManager).scheduledAlarms
        assertEquals(1, scheduled.size)
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(ownerId = key.ownerId, taskId = key.taskId),
            Intent(context, ReminderAlarmReceiver::class.java).apply {
                data = Uri.parse("petcare://reminder/7/31")
            },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        assertTrue(pending != null)
        val savedIntent = shadowOf(pending!!).savedIntent
        assertEquals(7L, savedIntent.getLongExtra(EXTRA_OWNER_ID, -1L))
        assertEquals(key.taskId, savedIntent.getLongExtra(EXTRA_TASK_ID, -1L))
        assertEquals(
            second.toLocalDate().toEpochDay(),
            savedIntent.getLongExtra(EXTRA_DUE_EPOCH_DAY, Long.MIN_VALUE)
        )
        assertEquals(
            second.toInstant().toEpochMilli(),
            savedIntent.getLongExtra(EXTRA_TRIGGER_AT_MILLIS, 0L)
        )
        assertEquals("petcare://reminder/7/31", savedIntent.dataString)
    }

    @Test
    fun `receiver declarations are not externally exported`() {
        val packageManager = context.packageManager
        val alarmInfo = packageManager.getReceiverInfo(
            ComponentName(context, ReminderAlarmReceiver::class.java),
            0
        )
        val rescheduleInfo = packageManager.getReceiverInfo(
            ComponentName(context, ReminderRescheduleReceiver::class.java),
            0
        )

        assertFalse(alarmInfo.exported)
        assertFalse(rescheduleInfo.exported)
        val expectedActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )
        expectedActions.forEach { action ->
            val receivers = packageManager.queryBroadcastReceivers(
                Intent(action),
                PackageManager.MATCH_ALL
            )
            assertTrue(
                "Missing declared receiver for $action",
                receivers.any { it.activityInfo?.name == ReminderRescheduleReceiver::class.java.name }
            )
        }
    }

    @Test
    fun `alarm receiver rejects malformed payload delivered through PendingIntent`() {
        val malformedIntents = listOf(
            Intent(context, ReminderAlarmReceiver::class.java),
            Intent(context, ReminderAlarmReceiver::class.java).apply {
                putExtra(EXTRA_OWNER_ID, 0L)
                putExtra(EXTRA_TASK_ID, 31L)
                putExtra(EXTRA_DUE_EPOCH_DAY, LocalDate.now().toEpochDay())
            }
        )
        malformedIntents.forEachIndexed { index, malformed ->
            PendingIntent.getBroadcast(
                context,
                80_031 + index,
                malformed,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ).send()
        }

        assertTrue(ReminderAlarmStore(context).all().isEmpty())
        assertEquals(
            0,
            shadowOf(context.getSystemService(NotificationManager::class.java))
                .allNotifications.size
        )
    }

    @Test
    fun `reschedule receiver rejects unrelated broadcasts before goAsync`() {
        val receiver = ReminderRescheduleReceiver()

        receiver.onReceive(context, Intent("com.petcare.app.UNRELATED_BROADCAST"))

        assertTrue(ReminderAlarmStore(context).all().isEmpty())
    }

    private fun task(id: Long, name: String = "Daily care") = CareTask(
        id = id,
        petId = 1,
        name = name,
        category = TaskCategory.FEEDING.name,
        frequency = Frequency.DAILY.name,
        startDateEpochDay = LocalDate.now().toEpochDay(),
        timeMinutes = 8 * 60,
        reminderEnabled = true
    )

    private fun requestCode(ownerId: Long, taskId: Long): Int =
        ((ownerId * 31L + taskId) xor (ownerId ushr 32) xor (taskId ushr 32))
            .toInt()
            .and(0x7fffffff)
            .coerceAtLeast(1)
}