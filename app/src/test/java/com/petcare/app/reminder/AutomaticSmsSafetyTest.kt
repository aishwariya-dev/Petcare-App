package com.petcare.app.reminder

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.petcare.app.data.local.PetCareDatabase
import com.petcare.app.data.TestEnvironment
import com.petcare.app.data.preferences.UserPreferences
import com.petcare.app.data.repository.AuthRepository
import com.petcare.app.data.repository.SmsReminderRepository
import com.petcare.app.data.repository.TaskRepository
import com.petcare.app.model.CareTask
import com.petcare.app.model.Frequency
import com.petcare.app.model.Pet
import com.petcare.app.model.Species
import com.petcare.app.model.TaskCategory
import com.petcare.app.util.ImageStorage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AutomaticSmsSafetyTest {
    private lateinit var context: Context
    private lateinit var database: PetCareDatabase
    private lateinit var auth: AuthRepository
    private lateinit var tasks: TaskRepository
    private lateinit var sms: SmsReminderRepository
    private lateinit var scheduler: ReminderAlarmScheduler

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, PetCareDatabase::class.java)
            .allowMainThreadQueries().build()
        auth = AuthRepository(database.userDao(), UserPreferences(context))
        tasks = TaskRepository(database.careTaskDao(), ImageStorage(context), auth)
        sms = SmsReminderRepository(database.careTaskDao(), database.smsReminderOccurrenceDao(), database.petDao())
        scheduler = ReminderAlarmScheduler(context)
        runBlocking { auth.logout() }
    }

    @After fun tearDown() {
        runBlocking { auth.logout() }
        database.close()
    }

    @Test fun `SMS is default off and recipient persists only when explicitly set`() = runBlocking {
        val user = auth.register("SMS owner", "sms-default@test.local", "secret1").getOrThrow()
        val petId = database.petDao().insert(Pet(ownerId = user.id, name = "Milo", species = Species.DOG.name))
        val defaultId = tasks.save(task(petId).copy(smsReminderEnabled = false, smsRecipient = ""))
        val configuredId = tasks.save(task(petId).copy(smsReminderEnabled = true, smsRecipient = "+44 7700 900000"))

        assertFalse(tasks.getTask(defaultId)!!.smsReminderEnabled)
        assertEquals("", tasks.getTask(defaultId)!!.smsRecipient)
        assertTrue(tasks.getTask(configuredId)!!.smsReminderEnabled)
        assertEquals("+44 7700 900000", tasks.getTask(configuredId)!!.smsRecipient)
    }

    @Test fun `duplicate callbacks claim automatic SMS once`() = runBlocking {
        val (user, saved) = signedInSmsTask()
        val fake = RecordingSender()
        val coordinator = coordinator(fake)

        coordinator.handleAlarm(user.id, saved.id, LocalDate.now().toEpochDay(), System.currentTimeMillis())
        coordinator.handleAlarm(user.id, saved.id, LocalDate.now().toEpochDay(), System.currentTimeMillis())

        assertEquals(1, fake.submissions)
    }

    @Test fun `multipart callbacks are idempotent and failure cannot become sent`() = runBlocking {
        val (user, saved) = signedInSmsTask()
        val failed = sms.claim(user.id, saved.id, LocalDate.now().toEpochDay())
        sms.markSubmitted(failed, expectedParts = 2)
        sms.recordPartCallback(failed, 0, false, "part failed")
        sms.recordPartCallback(failed, 1, true, "part accepted")
        assertEquals("FAILED", database.smsReminderOccurrenceDao().findOccurrence(failed)!!.status)

        val allAccepted = sms.claim(user.id, saved.id, LocalDate.now().plusDays(1).toEpochDay())
        sms.markSubmitted(allAccepted, expectedParts = 2)
        sms.recordPartCallback(allAccepted, 0, true, "part accepted")
        // Duplicate callback with a contradictory result cannot overwrite the
        // first accepted callback for this unique part identity.
        sms.recordPartCallback(allAccepted, 0, false, "duplicate failure")
        assertEquals("SUBMITTED", database.smsReminderOccurrenceDao().findOccurrence(allAccepted)!!.status)
        sms.recordPartCallback(allAccepted, 1, true, "part accepted")
        assertEquals("SENT", database.smsReminderOccurrenceDao().findOccurrence(allAccepted)!!.status)
    }

    @Test fun `completed deleted and logged out alarms never reach sender`() = runBlocking {
        val (user, saved) = signedInSmsTask()
        val fake = RecordingSender()
        val coordinator = coordinator(fake)

        tasks.setCompleted(saved, true)
        coordinator.handleAlarm(user.id, saved.id, LocalDate.now().toEpochDay(), System.currentTimeMillis())
        assertEquals(0, fake.submissions)

        val second = task(saved.petId).let { it.copy(id = tasks.save(it)) }
        tasks.delete(second)
        coordinator.handleAlarm(user.id, second.id, LocalDate.now().toEpochDay(), System.currentTimeMillis())
        assertEquals(0, fake.submissions)

        auth.logout()
        coordinator.handleAlarm(user.id, saved.id, LocalDate.now().toEpochDay(), System.currentTimeMillis())
        assertEquals(0, fake.submissions)
    }

    @Test fun `unavailable statuses are actionable and never called by fake boundary`() {
        assertTrue(availabilityMessage(SmsAvailability.PermissionMissing).contains("permission"))
        assertTrue(availabilityMessage(SmsAvailability.DefaultSimUnavailable).contains("default SMS SIM"))
        assertTrue(availabilityMessage(SmsAvailability.TelephonyUnavailable).contains("no SMS service"))
    }

    @Test fun `missing permission default SIM and account switch do not submit SMS`() = runBlocking {
        val (user, saved) = signedInSmsTask()
        val unavailable = object : AutomaticSmsSender {
            var submissions = 0
            override fun availability() = SmsAvailability.DefaultSimUnavailable
            override suspend fun submit(
                task: CareTask,
                message: String,
                occurrenceId: Long,
                onPrepared: suspend (Int) -> Unit
            ): Result<Unit> {
                submissions++
                return Result.success(Unit)
            }
        }
        coordinator(unavailable).handleAlarm(
            user.id, saved.id, LocalDate.now().toEpochDay(), System.currentTimeMillis()
        )
        assertEquals(0, unavailable.submissions)
        assertTrue(sms.observeLatestStatus(saved.id).first()!!.startsWith("UNAVAILABLE:"))

        val permissionDenied = object : AutomaticSmsSender {
            var submissions = 0
            override fun availability() = SmsAvailability.PermissionMissing
            override suspend fun submit(
                task: CareTask,
                message: String,
                occurrenceId: Long,
                onPrepared: suspend (Int) -> Unit
            ): Result<Unit> {
                submissions++
                return Result.success(Unit)
            }
        }
        val another = task(saved.petId).let { it.copy(id = tasks.save(it)) }
        coordinator(permissionDenied).handleAlarm(
            user.id, another.id, LocalDate.now().toEpochDay(), System.currentTimeMillis()
        )
        assertEquals(0, permissionDenied.submissions)

        val ready = RecordingSender()
        auth.register("Other owner", "other-${System.nanoTime()}@test.local", "secret1").getOrThrow()
        coordinator(ready).handleAlarm(user.id, saved.id, LocalDate.now().toEpochDay(), System.currentTimeMillis())
        assertEquals(0, ready.submissions)
    }

    @Test fun `notification reports the SMS outcome instead of failing silently`() = runBlocking {
        val application = context.applicationContext as Application
        org.robolectric.Shadows.shadowOf(application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        org.robolectric.Shadows.shadowOf(manager).setNotificationsEnabled(true)
        fun lastNotificationText(): String =
            org.robolectric.Shadows.shadowOf(manager).allNotifications.last().extras.getString("android.text").orEmpty()

        val (user, saved) = signedInSmsTask()
        val withNotification = saved.copy(reminderEnabled = true).also { tasks.save(it) }
        val today = LocalDate.now().toEpochDay()

        coordinator(RecordingSender()).handleAlarm(user.id, withNotification.id, today, System.currentTimeMillis())
        assertTrue(lastNotificationText(), lastNotificationText().endsWith("SMS sending to ${saved.smsRecipient}"))

        // A repeated alarm for the same occurrence must not resend and must
        // keep reporting the earlier outcome instead of a silent notification.
        val recorder = RecordingSender()
        coordinator(recorder).handleAlarm(user.id, withNotification.id, today, System.currentTimeMillis())
        assertEquals(0, recorder.submissions)
        assertTrue(lastNotificationText(), lastNotificationText().endsWith("SMS sending to ${saved.smsRecipient}"))

        val noSim = object : AutomaticSmsSender {
            override fun availability() = SmsAvailability.DefaultSimUnavailable
            override suspend fun submit(task: CareTask, message: String, occurrenceId: Long, onPrepared: suspend (Int) -> Unit) =
                Result.success(Unit)
        }
        val second = task(saved.petId).copy(reminderEnabled = true).let { it.copy(id = tasks.save(it)) }
        coordinator(noSim).handleAlarm(user.id, second.id, today, System.currentTimeMillis())
        assertTrue(lastNotificationText(), lastNotificationText().contains("SMS not sent — no SIM is ready"))

        val failing = object : AutomaticSmsSender {
            override fun availability() = SmsAvailability.Ready
            override suspend fun submit(task: CareTask, message: String, occurrenceId: Long, onPrepared: suspend (Int) -> Unit) =
                Result.failure<Unit>(IllegalStateException("modem refused"))
        }
        val third = task(saved.petId).copy(reminderEnabled = true).let { it.copy(id = tasks.save(it)) }
        coordinator(failing).handleAlarm(user.id, third.id, today, System.currentTimeMillis())
        assertTrue(lastNotificationText(), lastNotificationText().contains("SMS not sent — modem refused"))
        assertTrue(sms.observeLatestStatus(third.id).first()!!.startsWith("FAILED:"))

        // A sender that throws (rather than returning a failure) must not
        // suppress the notification.
        val throwing = object : AutomaticSmsSender {
            override fun availability() = SmsAvailability.Ready
            override suspend fun submit(task: CareTask, message: String, occurrenceId: Long, onPrepared: suspend (Int) -> Unit): Result<Unit> =
                throw IllegalStateException("telephony crashed")
        }
        val fourth = task(saved.petId).copy(reminderEnabled = true).let { it.copy(id = tasks.save(it)) }
        coordinator(throwing).handleAlarm(user.id, fourth.id, today, System.currentTimeMillis())
        assertTrue(lastNotificationText(), lastNotificationText().contains("SMS not sent — telephony crashed"))
    }

    @Test fun `stale edited alarm does not submit SMS`() = runBlocking {
        val (user, saved) = signedInSmsTask()
        val edited = saved.copy(
            frequency = Frequency.ONCE.name,
            startDateEpochDay = LocalDate.now().plusDays(1).toEpochDay()
        )
        tasks.save(edited)
        val fake = RecordingSender()

        coordinator(fake).handleAlarm(
            user.id, saved.id, LocalDate.now().toEpochDay(), System.currentTimeMillis() - 120 * 60_000L
        )

        assertEquals(0, fake.submissions)
    }

    @Test fun `disabling or logging out ahead of handoff prevents sender call`() = runBlocking {
        val (user, saved) = signedInSmsTask()
        val disabled = saved.copy(smsReminderEnabled = false)
        tasks.save(disabled)
        val fake = RecordingSender()
        coordinator(fake).handleAlarm(user.id, saved.id, LocalDate.now().toEpochDay(), System.currentTimeMillis())
        assertEquals(0, fake.submissions)

        auth.logout()
        coordinator(fake).handleAlarm(user.id, saved.id, LocalDate.now().toEpochDay(), System.currentTimeMillis())
        assertEquals(0, fake.submissions)
    }

    @Test fun `concurrent disable and logout ahead of authorization gate prevent handoff`() = runBlocking {
        val (user, first) = signedInSmsTask()
        val reached = CompletableDeferred<Unit>()
        val continueAlarm = CompletableDeferred<Unit>()
        val fake = RecordingSender()
        val coordinator = ReminderCoordinator(
            auth, tasks, scheduler, sms, fake,
            beforeSmsAuthorization = {
                reached.complete(Unit)
                continueAlarm.await()
            }
        )
        val alarm = launch {
            coordinator.handleAlarm(user.id, first.id, LocalDate.now().toEpochDay(), System.currentTimeMillis())
        }
        reached.await()
        // This write obtains/releases the same gate before the alarm can do
        // its direct final DAO read or reach the sender.
        tasks.save(first.copy(smsReminderEnabled = false))
        continueAlarm.complete(Unit)
        alarm.join()
        assertEquals(0, fake.submissions)

        val second = task(first.petId).let { it.copy(id = tasks.save(it)) }
        val logoutReached = CompletableDeferred<Unit>()
        val continueLogoutAlarm = CompletableDeferred<Unit>()
        val logoutCoordinator = ReminderCoordinator(
            auth, tasks, scheduler, sms, fake,
            beforeSmsAuthorization = {
                logoutReached.complete(Unit)
                continueLogoutAlarm.await()
            }
        )
        val logoutAlarm = launch {
            logoutCoordinator.handleAlarm(user.id, second.id, LocalDate.now().toEpochDay(), System.currentTimeMillis())
        }
        logoutReached.await()
        auth.logout()
        continueLogoutAlarm.complete(Unit)
        logoutAlarm.join()
        assertEquals(0, fake.submissions)
    }

    // These two tests use TestEnvironment for isolated preferences: the
    // production DataStore delegate is process-wide, and account 1 exists in
    // every test, so a switch written by one test would leak into the next.
    @Test fun `automatic SMS is cancelled when the account never agreed to it`() = runBlocking {
        TestEnvironment(context).use { env ->
            val sms = env.smsRepository()
            val (user, saved) = env.signedInSmsTask()
            val fake = RecordingSender()
            ReminderCoordinator(env.auth, env.tasks, scheduler, sms, fake, preferences = env.preferences)
                .handleAlarm(user.id, saved.id, LocalDate.now().toEpochDay(), System.currentTimeMillis())

            assertEquals(0, fake.submissions)
            val status = env.database.smsReminderOccurrenceDao().observeLatestStatus(saved.id).first().orEmpty()
            assertTrue(status, status.startsWith("CANCELLED"))
            assertTrue(status, status.contains("not agreed"))

            // Once consent is stored the next occurrence goes out normally.
            env.preferences.setSmsConsent(user.id)
            val second = task(saved.petId).let { it.copy(id = env.tasks.save(it)) }
            ReminderCoordinator(env.auth, env.tasks, scheduler, sms, fake, preferences = env.preferences)
                .handleAlarm(user.id, second.id, LocalDate.now().toEpochDay(), System.currentTimeMillis())
            assertEquals(1, fake.submissions)
        }
    }

    @Test fun `reminders switched off after the early check still stop the SMS inside the gate`() = runBlocking {
        TestEnvironment(context).use { env ->
            val sms = env.smsRepository()
            val (user, saved) = env.signedInSmsTask()
            env.preferences.setSmsConsent(user.id)
            val reached = CompletableDeferred<Unit>()
            val continueAlarm = CompletableDeferred<Unit>()
            val fake = RecordingSender()
            val coordinator = ReminderCoordinator(
                env.auth, env.tasks, scheduler, sms, fake,
                beforeSmsAuthorization = {
                    reached.complete(Unit)
                    continueAlarm.await()
                },
                preferences = env.preferences
            )
            val alarm = launch {
                coordinator.handleAlarm(user.id, saved.id, LocalDate.now().toEpochDay(), System.currentTimeMillis())
            }
            reached.await()
            // The master switch flips between the early check and the gate.
            env.auth.smsAuthorizationGate.withLock { env.preferences.setRemindersEnabled(user.id, false) }
            continueAlarm.complete(Unit)
            alarm.join()

            assertEquals(0, fake.submissions)
            val status = env.database.smsReminderOccurrenceDao().observeLatestStatus(saved.id).first().orEmpty()
            assertTrue(status, status.startsWith("CANCELLED"))
            assertTrue(status, status.contains("turned off"))
        }
    }

    @Test fun `legacy devices check the pre API 33 telephony feature`() {
        val manager = context.packageManager
        val expected = manager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        assertEquals(expected, hasSmsTelephonyFeature(manager, 32))
    }

    private fun TestEnvironment.smsRepository() =
        SmsReminderRepository(database.careTaskDao(), database.smsReminderOccurrenceDao(), database.petDao())

    private suspend fun TestEnvironment.signedInSmsTask(): Pair<com.petcare.app.model.User, CareTask> {
        val user = auth.register("SMS owner", "sms-${System.nanoTime()}@test.local", "secret1").getOrThrow()
        val petId = database.petDao().insert(Pet(ownerId = user.id, name = "Milo", species = Species.DOG.name))
        return user to task(petId).let { it.copy(id = tasks.save(it)) }
    }

    private suspend fun signedInSmsTask(): Pair<com.petcare.app.model.User, CareTask> {
        val user = auth.register("SMS owner", "sms-${System.nanoTime()}@test.local", "secret1").getOrThrow()
        val petId = database.petDao().insert(Pet(ownerId = user.id, name = "Milo", species = Species.DOG.name))
        val saved = task(petId).copy(id = tasks.save(task(petId)))
        return user to saved
    }

    private fun coordinator(sender: AutomaticSmsSender) =
        ReminderCoordinator(auth, tasks, scheduler, sms, sender)

    private fun task(petId: Long) = CareTask(
        petId = petId,
        name = "Give medication",
        category = TaskCategory.MEDICATION.name,
        frequency = Frequency.DAILY.name,
        startDateEpochDay = LocalDate.now().toEpochDay(),
        timeMinutes = LocalTime.now().hour * 60 + LocalTime.now().minute,
        smsReminderEnabled = true,
        smsRecipient = "+44 7700 900000",
        notes = "With food"
    )

    private class RecordingSender : AutomaticSmsSender {
        var submissions = 0
        override fun availability() = SmsAvailability.Ready
        override suspend fun submit(
            task: CareTask,
            message: String,
            occurrenceId: Long,
            onPrepared: suspend (Int) -> Unit
        ): Result<Unit> {
            submissions++
            onPrepared(1)
            return Result.success(Unit)
        }
    }
}