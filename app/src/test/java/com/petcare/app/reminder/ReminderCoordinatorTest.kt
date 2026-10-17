package com.petcare.app.reminder

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.petcare.app.data.local.PetCareDatabase
import com.petcare.app.data.preferences.UserPreferences
import com.petcare.app.data.repository.AuthRepository
import com.petcare.app.data.repository.TaskRepository
import com.petcare.app.model.CareTask
import com.petcare.app.model.Frequency
import com.petcare.app.model.Pet
import com.petcare.app.model.Species
import com.petcare.app.model.TaskCategory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReminderCoordinatorTest {
    private lateinit var context: Context
    private lateinit var database: PetCareDatabase
    private lateinit var auth: AuthRepository
    private lateinit var tasks: TaskRepository
    private lateinit var scheduler: ReminderAlarmScheduler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, PetCareDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        auth = AuthRepository(database.userDao(), UserPreferences(context))
        tasks = TaskRepository(
            database.careTaskDao(),
            com.petcare.app.util.ImageStorage(context),
            auth
        )
        runBlocking { auth.logout() }
        shadowOf(context.applicationContext as Application)
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        NotificationManagerCompat.from(context).cancelAll()
        context.getSharedPreferences("petcare_reminder_alarms", Context.MODE_PRIVATE)
            .edit().clear().commit()
        scheduler = ReminderAlarmScheduler(context)
    }

    @After
    fun tearDown() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        runBlocking { auth.logout() }
        database.close()
    }

    @Test
    fun `revoking exact access re-arms a matching reminder with the inexact fallback`() = runBlocking {
        val user = auth.register("Reminder User", "mode-revoke@test.local", "secret1").getOrThrow()
        val petId = database.petDao().insert(
            Pet(ownerId = user.id, name = "Milo", species = Species.DOG.name)
        )
        val due = LocalDate.now().plusDays(1)
        val saved = CareTask(
            petId = petId, name = "Morning medication", category = TaskCategory.MEDICATION.name,
            frequency = Frequency.ONCE.name, startDateEpochDay = due.toEpochDay(),
            timeMinutes = 8 * 60, reminderEnabled = true
        ).let { it.copy(id = tasks.save(it)) }
        val key = ReminderAlarmKey(user.id, saved.id)
        val coordinator = ReminderCoordinator(auth, tasks, scheduler)

        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        coordinator.refreshNow()
        assertEquals(0L, shadowOf(context.getSystemService(android.app.AlarmManager::class.java))
            .scheduledAlarms.single().windowLengthMs)

        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        coordinator.refreshNow()

        val scheduled = shadowOf(context.getSystemService(android.app.AlarmManager::class.java))
            .scheduledAlarms
        assertEquals(1, scheduled.size)
        assertEquals(-1L, scheduled.single().windowLengthMs)
        assertTrue(scheduler.tracked().containsKey(key))
    }

    @Test
    fun `SMS-only alarms move between inexact and exact when access changes`() = runBlocking {
        val user = auth.register("SMS User", "sms-mode@test.local", "secret1").getOrThrow()
        val petId = database.petDao().insert(
            Pet(ownerId = user.id, name = "Luna", species = Species.CAT.name)
        )
        val due = LocalDate.now().plusDays(1)
        val saved = CareTask(
            petId = petId, name = "Evening medication", category = TaskCategory.MEDICATION.name,
            frequency = Frequency.ONCE.name, startDateEpochDay = due.toEpochDay(),
            timeMinutes = 18 * 60, reminderEnabled = false,
            smsReminderEnabled = true, smsRecipient = "+1555000100"
        ).let { it.copy(id = tasks.save(it)) }
        val key = ReminderAlarmKey(user.id, saved.id)
        val coordinator = ReminderCoordinator(auth, tasks, scheduler)
        val shadowAlarmManager = shadowOf(context.getSystemService(android.app.AlarmManager::class.java))

        coordinator.refreshNow()
        assertEquals(-1L, shadowAlarmManager.scheduledAlarms.single().windowLengthMs)

        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        coordinator.refreshNow()
        assertEquals(0L, shadowAlarmManager.scheduledAlarms.single().windowLengthMs)

        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        coordinator.refreshNow()
        assertEquals(-1L, shadowAlarmManager.scheduledAlarms.single().windowLengthMs)
        assertTrue(scheduler.tracked().containsKey(key))
    }

    @Test
    fun `stale completed alarm cannot cancel the next recurring reminder`() = runBlocking {
        val user = auth.register("Reminder User", "completed@test.local", "secret1").getOrThrow()
        val petId = database.petDao().insert(
            Pet(ownerId = user.id, name = "Milo", species = Species.DOG.name)
        )
        val today = LocalDate.now()
        val task = CareTask(
            petId = petId, name = "Daily care", category = TaskCategory.FEEDING.name,
            frequency = Frequency.DAILY.name, startDateEpochDay = today.toEpochDay(),
            timeMinutes = 0, reminderEnabled = true, lastCompletedEpochDay = today.toEpochDay()
        )
        val saved = task.copy(id = tasks.save(task))
        val future = today.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault())
        val key = ReminderAlarmKey(user.id, saved.id)
        scheduler.schedule(user.id, saved, future)
        val expected = scheduler.tracked()[key]

        ReminderCoordinator(auth, tasks, scheduler).handleAlarm(
            user.id, saved.id, today.toEpochDay(), System.currentTimeMillis() - 60_000
        )

        assertEquals(expected, scheduler.tracked()[key])
        assertEquals(today.plusDays(1).toEpochDay(), scheduler.dueEpochDay(key))
    }

    @Test
    fun `foreground preserves overdue alarm and reboot re-arms that occurrence`() = runBlocking {
        val user = auth.register("Reminder User", "overdue@test.local", "secret1").getOrThrow()
        val petId = database.petDao().insert(
            Pet(ownerId = user.id, name = "Milo", species = Species.DOG.name)
        )
        val due = LocalDate.now().minusDays(1)
        val task = CareTask(
            petId = petId, name = "Care task", category = TaskCategory.MEDICATION.name,
            frequency = Frequency.ONCE.name, startDateEpochDay = due.toEpochDay(),
            timeMinutes = 480, reminderEnabled = true
        )
        val saved = task.copy(id = tasks.save(task))
        val key = ReminderAlarmKey(user.id, saved.id)
        val overdueAt = System.currentTimeMillis() - 86_400_000
        ReminderAlarmStore(context).put(key, overdueAt, due.toEpochDay(), scheduler.scheduleSignature(saved))
        val coordinator = ReminderCoordinator(auth, tasks, scheduler)

        coordinator.refreshNow()
        assertEquals(overdueAt, scheduler.tracked()[key])
        coordinator.refreshNow(forceReschedule = true)
        assertTrue(scheduler.tracked().getValue(key) > overdueAt)
        assertEquals(due.toEpochDay(), scheduler.dueEpochDay(key))
    }

    @Test
    fun `one-off alarm leaves notification visible and logout dismisses it`() = runBlocking {
        val user = auth.register("Reminder User", "reminder@test.local", "secret1").getOrThrow()
        val petId = database.petDao().insert(
            Pet(ownerId = user.id, name = "Milo", species = Species.DOG.name)
        )
        val due = LocalDate.now().minusDays(1)
        val oldTime = LocalTime.now()
        val task = CareTask(
            petId = petId,
            name = "Give medicine",
            category = TaskCategory.MEDICATION.name,
            frequency = Frequency.ONCE.name,
            startDateEpochDay = due.toEpochDay(),
            timeMinutes = oldTime.hour * 60 + oldTime.minute,
            reminderEnabled = true
        )
        val taskId = tasks.save(task)
        val coordinator = ReminderCoordinator(auth, tasks, scheduler)

        coordinator.handleAlarm(
            ownerId = user.id,
            taskId = taskId,
            dueEpochDay = due.toEpochDay(),
            triggerAtMillis = System.currentTimeMillis() - 5 * 60_000L
        )

        assertEquals(
            1,
            shadowOf(context.getSystemService(NotificationManager::class.java))
                .allNotifications.size
        )
        assertTrue(scheduler.postedNotifications().isNotEmpty())

        // Opening the app again must not dismiss an already delivered one-off.
        coordinator.refreshNow()
        assertEquals(1, shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.size)

        auth.logout()
        coordinator.refreshNow()

        assertEquals(
            0,
            shadowOf(context.getSystemService(NotificationManager::class.java))
                .allNotifications.size
        )
    }
}