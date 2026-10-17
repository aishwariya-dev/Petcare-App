package com.petcare.app.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.petcare.app.model.CareTask
import com.petcare.app.model.Frequency
import com.petcare.app.model.TaskCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Exercises the Android boundary without waiting for a real alarm. The pure
 * date/time behaviour is covered in TaskScheduleTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReminderAlarmSchedulerTest {
    private lateinit var context: Context
    private lateinit var scheduler: ReminderAlarmScheduler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("petcare_reminder_alarms", Context.MODE_PRIVATE)
            .edit().clear().commit()
        NotificationManagerCompat.from(context).cancelAll()
        context.getSystemService(AlarmManager::class.java)?.cancelAll()
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        shadowOf(context.applicationContext as Application)
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        scheduler = ReminderAlarmScheduler(context)
    }

    @Test
    fun `retiring one-off alarm leaves its posted notification visible`() {
        val task = task(id = 17, frequency = Frequency.ONCE)
        assertTrue(ReminderNotification.postTaskNotification(context, task))
        scheduler.markNotificationPosted(ReminderAlarmKey(5, task.id))

        scheduler.retire(ReminderAlarmKey(5, task.id))

        val notifications = shadowOf(
            context.getSystemService(NotificationManager::class.java)
        ).allNotifications
        assertEquals(1, notifications.size)
        assertTrue(scheduler.postedNotifications().contains(ReminderAlarmKey(5, task.id)))
    }

    @Test
    fun `cancel all dismisses a posted one-off after its alarm was retired`() {
        val task = task(id = 18, frequency = Frequency.ONCE)
        assertTrue(ReminderNotification.postTaskNotification(context, task))
        scheduler.markNotificationPosted(ReminderAlarmKey(6, task.id))
        scheduler.retire(ReminderAlarmKey(6, task.id))

        scheduler.cancelAll()

        assertEquals(
            0,
            shadowOf(context.getSystemService(NotificationManager::class.java))
                .allNotifications.size
        )
        assertFalse(scheduler.postedNotifications().contains(ReminderAlarmKey(6, task.id)))
    }

    @Test
    fun `cancelling an edited task removes its stale tracked alarm`() {
        val task = task(id = 19, frequency = Frequency.DAILY)
        scheduler.schedule(
            ownerId = 7,
            task = task,
            triggerAt = ZonedDateTime.now().plusHours(1)
        )
        assertTrue(scheduler.tracked().containsKey(ReminderAlarmKey(7, task.id)))

        scheduler.cancel(ReminderAlarmKey(7, task.id))

        assertFalse(scheduler.tracked().containsKey(ReminderAlarmKey(7, task.id)))
    }

    @Test
    fun `exact alarm access produces an exact idle-tolerant alarm`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        try {
            scheduler.schedule(
                ownerId = 8,
                task = task(id = 21, frequency = Frequency.DAILY),
                triggerAt = ZonedDateTime.now().plusMinutes(5)
            )

            val scheduled = shadowOf(context.getSystemService(AlarmManager::class.java))
                .scheduledAlarms.single()
            assertEquals(0L, scheduled.windowLengthMs)
            assertTrue(scheduled.isAllowWhileIdle)
        } finally {
            ShadowAlarmManager.setCanScheduleExactAlarms(false)
        }
    }

    @Test
    fun `without exact alarm access it keeps an inexact fallback`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        scheduler.schedule(
            ownerId = 8,
            task = task(id = 22, frequency = Frequency.DAILY),
            triggerAt = ZonedDateTime.now().plusMinutes(5)
        )

        val scheduled = shadowOf(context.getSystemService(AlarmManager::class.java))
            .scheduledAlarms.single()
        assertEquals(-1L, scheduled.windowLengthMs)
        assertTrue(scheduled.isAllowWhileIdle)
    }

    @Test
    fun `a revocation during exact scheduling falls back to an inexact alarm`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        try {
            val racingScheduler = ReminderAlarmScheduler(
                context = context,
                setExactAlarm = { _, _, _ -> throw SecurityException("revoked during scheduling") }
            )

            racingScheduler.schedule(
                ownerId = 8,
                task = task(id = 23, frequency = Frequency.DAILY),
                triggerAt = ZonedDateTime.now().plusMinutes(5)
            )

            val scheduled = shadowOf(context.getSystemService(AlarmManager::class.java))
                .scheduledAlarms.single()
            assertEquals(-1L, scheduled.windowLengthMs)
            assertTrue(scheduled.isAllowWhileIdle)
            assertEquals(false, racingScheduler.exactAlarm(ReminderAlarmKey(8, 23)))
        } finally {
            ShadowAlarmManager.setCanScheduleExactAlarms(false)
        }
    }

    @Test
    fun `denied notification permission does not crash a task notification`() {
        shadowOf(context.applicationContext as Application)
            .denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertFalse(ReminderNotification.postTaskNotification(context, task(1, Frequency.DAILY)))
    }

    private fun task(id: Long, frequency: Frequency) = CareTask(
        id = id,
        petId = 1,
        name = "Medication",
        category = TaskCategory.MEDICATION.name,
        frequency = frequency.name,
        startDateEpochDay = LocalDate.now().toEpochDay(),
        timeMinutes = 8 * 60,
        reminderEnabled = true
    )
}