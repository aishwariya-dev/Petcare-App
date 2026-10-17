package com.petcare.app.data

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import android.content.Context
import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.petcare.app.data.local.PetCareDatabase
import com.petcare.app.data.preferences.UserPreferences
import com.petcare.app.data.repository.AuthRepository
import com.petcare.app.data.repository.ExpenseRepository
import com.petcare.app.data.repository.LocationRepository
import com.petcare.app.data.repository.PetRepository
import com.petcare.app.data.repository.TaskRepository
import com.petcare.app.model.CareTask
import com.petcare.app.model.Expense
import com.petcare.app.model.ExpenseCategory
import com.petcare.app.model.Frequency
import com.petcare.app.model.LocationType
import com.petcare.app.model.Pet
import com.petcare.app.model.PetLocation
import com.petcare.app.model.Species
import com.petcare.app.model.TaskCategory
import com.petcare.app.ui.screens.settings.ProfileViewModel
import com.petcare.app.util.ImageStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Room-backed JVM tests for the local-account boundary.
 *
 * These intentionally exercise the repositories against the generated Room
 * implementation rather than mocks: an owner predicate that is missing from
 * a DAO query is exactly the class of regression this suite is meant to catch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LocalDataSafetyTest {

    private lateinit var database: PetCareDatabase
    private lateinit var auth: AuthRepository
    private lateinit var petRepository: PetRepository
    private lateinit var taskRepository: TaskRepository
    private lateinit var expenseRepository: ExpenseRepository
    private lateinit var locationRepository: LocationRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PetCareDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        auth = AuthRepository(database.userDao(), UserPreferences(context))
        val imageStorage = ImageStorage(context)
        petRepository = PetRepository(
            database.petDao(),
            database.careTaskDao(),
            imageStorage,
            auth
        )
        taskRepository = TaskRepository(database.careTaskDao(), imageStorage, auth)
        expenseRepository = ExpenseRepository(database.expenseDao(), auth)
        locationRepository = LocationRepository(database.petLocationDao(), auth)
        // UserPreferences is file-backed even though Room is in memory.
        // Explicitly clear the session so each test starts at a boundary.
        runBlocking { auth.logout() }
    }

    @After
    fun tearDown() {
        runBlocking { auth.logout() }
        database.close()
    }

    @Test
    fun `registration login and logout use the local session`() = runBlocking {
        val registered = auth.register(
            name = " Alice Carter ",
            email = "ALICE@example.com",
            password = "secret1",
            phoneNumber = "+1 555 0100"
        ).getOrThrow()

        assertEquals("Alice Carter", registered.name)
        assertEquals("alice@example.com", registered.email)
        assertFalse(registered.passwordHash == "secret1")
        assertNotNull(auth.restoreSession())

        auth.logout()
        val duplicate = auth.register("Another Alice", " alice@EXAMPLE.com ", "secret1")
        assertTrue(duplicate.isFailure)
        assertEquals(
            "An account with this email already exists.",
            duplicate.exceptionOrNull()!!.message
        )

        auth.logout()
        assertNull(auth.restoreSession())
        assertTrue(auth.login("alice@example.com", "not-the-password").isFailure)
        assertNull(auth.restoreSession())

        val loggedIn = auth.login(" alice@example.com ", "secret1").getOrThrow()
        assertEquals(registered.id, loggedIn.id)
        assertEquals(registered.id, auth.signedInUserId())

        auth.logout()
        assertNull(auth.signedInUserId())
    }

    @Test
    fun `profile fields update atomically and require the signed in owner`() = runBlocking {
        val alice = auth.register("Alice", "alice@profile.test", "secret1").getOrThrow()
        val originalPasswordHash = alice.passwordHash

        auth.updateProfile(
            user = alice,
            name = "Alice Updated",
            phoneNumber = "+1 555 0199",
            photoPath = "/data/user/0/com.petcare.app/files/images/profile.jpg"
        )

        val saved = database.userDao().findById(alice.id)
        assertNotNull(saved)
        assertEquals("Alice Updated", saved!!.name)
        assertEquals("+1 555 0199", saved.phoneNumber)
        assertEquals(
            "/data/user/0/com.petcare.app/files/images/profile.jpg",
            saved.photoPath
        )
        assertEquals(originalPasswordHash, saved.passwordHash)

        auth.logout()
        val bob = auth.register("Bob", "bob@profile.test", "secret1").getOrThrow()
        assertTrue(
            runCatching {
                auth.updateProfile(alice, "Hijacked", "000", "/tmp/hijacked.jpg")
            }.isFailure
        )
        assertEquals("Alice Updated", database.userDao().findById(alice.id)!!.name)
        assertEquals("Bob", database.userDao().findById(bob.id)!!.name)
    }

    @Test
    fun `failed cross account save preserves the existing row and photo path`() = runBlocking {
        val alice = auth.register("Alice", "alice@preserve.test", "secret1").getOrThrow()
        val existing = Pet(
            ownerId = alice.id,
            name = "Max",
            species = Species.DOG.name,
            photoPath = "/data/user/0/com.petcare.app/files/images/max.jpg"
        )
        val petId = petRepository.save(existing)
        val saved = petRepository.getPet(petId)!!

        auth.logout()
        val bob = auth.register("Bob", "bob@preserve.test", "secret1").getOrThrow()
        val forged = saved.copy(
            ownerId = bob.id,
            name = "Not Max",
            photoPath = "/data/user/0/com.petcare.app/files/images/should-not-delete.jpg"
        )

        assertTrue(runCatching { petRepository.save(forged) }.isFailure)
        val unchanged = database.petDao().findById(petId, alice.id)
        assertNotNull(unchanged)
        assertEquals("Max", unchanged!!.name)
        assertEquals(saved.photoPath, unchanged.photoPath)
    }

    @Test
    fun `delete then undo preserves the task and photo against delayed cleanup`() = runBlocking<Unit> {
        val user = auth.register("Owner", "undo@example.test", "secret1").getOrThrow()
        val petId = petRepository.save(Pet(ownerId = user.id, name = "Pet", species = Species.DOG.name))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val image = java.io.File(context.filesDir, "images/undo-photo.jpg").apply {
            parentFile!!.mkdirs()
            writeText("kept through undo")
        }
        val task = CareTask(
            petId = petId, name = "Care", category = TaskCategory.FEEDING.name,
            frequency = Frequency.DAILY.name, startDateEpochDay = LocalDate.now().toEpochDay(),
            timeMinutes = 480, imagePath = image.absolutePath
        )
        val saved = task.copy(id = taskRepository.save(task))
        assertTrue(taskRepository.delete(saved))
        assertNull(taskRepository.getTask(saved.id))
        assertTrue(image.exists())
        assertTrue(taskRepository.restore(saved))
        taskRepository.discardImage(saved)
        assertEquals(saved, taskRepository.getTask(saved.id))
        assertTrue(image.exists())
        image.delete()
    }

    @Test
    fun `a second local account cannot read or mutate pet task or expense ids`() = runBlocking {
        val alice = auth.register("Alice", "alice@owner.test", "secret1").getOrThrow()
        val petId = petRepository.save(
            Pet(
                ownerId = alice.id,
                name = "Max",
                species = Species.DOG.name,
                photoPath = "/data/user/0/com.petcare.app/files/images/max.jpg"
            )
        )
        val alicePet = petRepository.getPet(petId)!!
        val taskId = taskRepository.save(
            CareTask(
                petId = petId,
                name = "Walk",
                category = TaskCategory.EXERCISE.name,
                frequency = Frequency.DAILY.name,
                startDateEpochDay = LocalDate.now().toEpochDay(),
                timeMinutes = 8 * 60,
                imagePath = "/data/user/0/com.petcare.app/files/images/walk.jpg"
            )
        )
        val aliceTask = taskRepository.getTask(taskId)!!
        val completionDay = LocalDate.now()
        assertTrue(taskRepository.setCompleted(aliceTask, completed = true, today = completionDay))
        val expenseId = expenseRepository.save(
            Expense(
                ownerId = alice.id,
                petId = petId,
                amount = 12.50,
                category = ExpenseCategory.FOOD.name,
                description = "Food"
            )
        )
        val aliceExpense = expenseRepository.getExpense(expenseId)!!

        auth.logout()
        val bob = auth.register("Bob", "bob@owner.test", "secret1").getOrThrow()

        assertNull(petRepository.getPet(alicePet.id))
        assertNull(petRepository.observePet(alicePet.id).first())
        assertNull(taskRepository.getTask(aliceTask.id))
        assertNull(taskRepository.observeTask(aliceTask.id).first())
        assertNull(expenseRepository.getExpense(aliceExpense.id))
        assertEquals(emptyList<Pet>(), petRepository.observePets(alice.id).first())
        assertEquals(emptyList<CareTask>(), taskRepository.observeTasks(alice.id).first())
        assertEquals(emptyList<Expense>(), expenseRepository.observeExpenses(alice.id).first())

        assertTrue(
            runCatching {
                petRepository.save(alicePet.copy(name = "Hijacked", ownerId = bob.id))
            }.isFailure
        )
        assertTrue(runCatching { petRepository.delete(alicePet) }.isFailure)
        // Mutations report false when the owner is not present in the current
        // session, so callers cannot announce a stale-row success.
        assertFalse(taskRepository.delete(aliceTask))
        assertFalse(taskRepository.restore(aliceTask))
        assertFalse(taskRepository.setCompleted(aliceTask, completed = true))
        expenseRepository.delete(aliceExpense)
        assertTrue(
            runCatching {
                expenseRepository.save(aliceExpense.copy(ownerId = bob.id, amount = 999.0))
            }.isFailure
        )

        auth.logout()
        assertFalse(taskRepository.delete(aliceTask))
        assertFalse(taskRepository.restore(aliceTask))
        assertFalse(taskRepository.setCompleted(aliceTask, completed = false))

        assertEquals(
            completionDay.toEpochDay(),
            database.careTaskDao().findById(aliceTask.id, alice.id)!!.lastCompletedEpochDay
        )
        assertEquals(
            aliceExpense.amount,
            database.expenseDao().findById(aliceExpense.id, alice.id)!!.amount,
            0.0
        )
    }

    @Test
    fun `legacy location CRUD is also isolated by the local session`() = runBlocking {
        val alice = auth.register("Alice", "alice@location.test", "secret1").getOrThrow()
        val locationId = locationRepository.save(
            PetLocation(
                ownerId = alice.id,
                name = "Alice Vet",
                type = LocationType.VETERINARY.name,
                address = "1 Main Street"
            )
        )
        val saved = database.petLocationDao().findById(locationId, alice.id)!!

        auth.logout()
        val bob = auth.register("Bob", "bob@location.test", "secret1").getOrThrow()

        assertNull(locationRepository.getLocation(locationId))
        assertEquals(emptyList<PetLocation>(), locationRepository.observeLocations(alice.id).first())
        assertTrue(
            runCatching {
                locationRepository.save(saved.copy(ownerId = bob.id, name = "Hijacked"))
            }.isFailure
        )
        locationRepository.delete(saved)

        val unchanged = database.petLocationDao().findById(locationId, alice.id)
        assertNotNull(unchanged)
        assertEquals("Alice Vet", unchanged!!.name)
    }

    @Test
    fun `profile draft is reset when the local session switches accounts`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val profile = ProfileViewModel(
            authRepository = auth,
            imageStorage = ImageStorage(ApplicationProvider.getApplicationContext())
        )
        try {
            val alice = auth.register("Alice", "alice@profile-switch.test", "secret1").getOrThrow()
            awaitProfileUser(profile, alice.id)

            profile.onNameChange("Alice draft")
            auth.logout()
            val bob = auth.register("Bob", "bob@profile-switch.test", "secret1").getOrThrow()
            awaitProfileUser(profile, bob.id)

            assertEquals(bob.id, profile.state.value.user?.id)
            assertEquals("Bob", profile.state.value.name)
        } finally {
            profile.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    /**
     * DataStore and Room use real background executors, so draining the test
     * scheduler alone cannot guarantee that ProfileViewModel has observed the
     * new session.  Pump the Main test dispatcher while giving those
     * executors a bounded amount of real time to publish their state.
     */
    private suspend fun TestScope.awaitProfileUser(
        profile: ProfileViewModel,
        expectedUserId: Long,
        timeoutMillis: Long = 5_000L
    ) {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
        while (profile.state.value.user?.id != expectedUserId) {
            advanceUntilIdle()
            if (profile.state.value.user?.id == expectedUserId) return
            if (System.nanoTime() >= deadline) {
                throw AssertionError(
                    "ProfileViewModel did not observe user $expectedUserId " +
                        "within ${timeoutMillis}ms; state=${profile.state.value}"
                )
            }
            withContext(Dispatchers.IO) {
                Thread.sleep(10L)
            }
        }
    }
}