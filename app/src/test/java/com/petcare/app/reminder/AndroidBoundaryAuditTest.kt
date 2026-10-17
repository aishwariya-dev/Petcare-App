package com.petcare.app.reminder

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.petcare.app.model.CareTask
import com.petcare.app.model.Frequency
import com.petcare.app.model.TaskCategory
import com.petcare.app.util.DateTimeFormats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Android notification boundary checks that do not require a PetCareApp
 * lifecycle or a real notification shade.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AndroidBoundaryAuditTest {
    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = context.getSystemService(NotificationManager::class.java)
        NotificationManagerCompat.from(context).cancelAll()
        shadowOf(context.applicationContext as Application)
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(notificationManager).setNotificationsEnabled(true)
    }

    @Test
    fun `channel creation exposes stable id importance and description`() {
        ReminderNotification.ensureChannel(context)

        val channel = notificationManager.getNotificationChannel(ReminderNotification.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(ReminderNotification.CHANNEL_ID, channel!!.id)
        assertEquals("Care reminders", channel.name)
        assertEquals("care_reminders_v2", ReminderNotification.CHANNEL_ID)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertEquals("Reminders for scheduled pet-care tasks", channel.description)
    }

    @Test
    fun `manifest declares exact alarm access for user-controlled precise reminders`() {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )

        assertTrue(info.requestedPermissions.orEmpty().contains(Manifest.permission.SCHEDULE_EXACT_ALARM))
    }

    @Test
    fun `runtime permission and app notification setting both gate posting`() {
        val application = context.applicationContext as Application
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertFalse(ReminderNotification.hasRuntimePermission(context))
        assertFalse(ReminderNotification.canPost(context))
        assertFalse(ReminderNotification.postTaskNotification(context, task(1)))
        assertEquals(0, shadowOf(notificationManager).allNotifications.size)

        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(notificationManager).setNotificationsEnabled(false)

        assertTrue(ReminderNotification.hasRuntimePermission(context))
        assertFalse(ReminderNotification.canPost(context))
        assertFalse(ReminderNotification.postTaskNotification(context, task(1)))
        assertEquals(0, shadowOf(notificationManager).allNotifications.size)

        shadowOf(notificationManager).setNotificationsEnabled(true)
        assertTrue(ReminderNotification.canPost(context))
        assertTrue(ReminderNotification.postTaskNotification(context, task(1)))
    }

    @Test
    fun `a user-blocked reminder channel is not reported as able to post`() {
        ReminderNotification.ensureChannel(context)
        val channel = notificationManager.getNotificationChannel(ReminderNotification.CHANNEL_ID)!!
        channel.importance = NotificationManager.IMPORTANCE_NONE
        notificationManager.createNotificationChannel(channel)

        assertFalse(ReminderNotification.canPost(context))
        assertFalse(ReminderNotification.postTaskNotification(context, task(1)))
        assertEquals(0, shadowOf(notificationManager).allNotifications.size)
    }

    @Test
    fun `task notification contains task text and direct cancellation removes it`() {
        val task = task(id = 91)

        assertTrue(ReminderNotification.postTaskNotification(context, task))

        val notifications = shadowOf(notificationManager).allNotifications
        assertEquals(1, notifications.size)
        val notification = notifications.single()
        assertEquals(ReminderNotification.CHANNEL_ID, notification.channelId)
        assertEquals("Care reminder", notification.extras.getString("android.title"))
        assertEquals(
            "${DateTimeFormats.time(task.timeMinutes)} — ${task.name}",
            notification.extras.getString("android.text")
        )
        assertNotNull(notification.contentIntent)

        ReminderNotification.cancelTaskNotification(context, task.id)

        assertEquals(0, shadowOf(notificationManager).allNotifications.size)
    }

    private fun task(id: Long) = CareTask(
        id = id,
        petId = 1,
        name = "Give medication",
        category = TaskCategory.MEDICATION.name,
        frequency = Frequency.DAILY.name,
        startDateEpochDay = LocalDate.now().toEpochDay(),
        timeMinutes = 8 * 60,
        reminderEnabled = true
    )
}