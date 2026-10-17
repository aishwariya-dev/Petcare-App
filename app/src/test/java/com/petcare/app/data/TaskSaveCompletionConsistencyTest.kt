package com.petcare.app.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.petcare.app.model.CareTask
import com.petcare.app.model.Pet
import com.petcare.app.model.Species
import com.petcare.app.model.TaskCategory
import com.petcare.app.model.Frequency
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TaskSaveCompletionConsistencyTest {
    private lateinit var environment: TestEnvironment

    @Before
    fun setUp() {
        environment = TestEnvironment(ApplicationProvider.getApplicationContext<Context>())
        runBlocking { environment.auth.logout() }
    }

    @After
    fun tearDown() {
        runBlocking { environment.auth.logout() }
        environment.close()
    }

    @Test
    fun `stale edits preserve a newer completion and reset`() = runBlocking {
        val owner = environment.auth.register(
            "Task owner",
            "task-save-consistency@example.com",
            "secret1"
        ).getOrThrow()
        val petId = environment.pets.save(
            Pet(ownerId = owner.id, name = "Task pet", species = Species.DOG.name)
        )
        val today = LocalDate.of(2026, 9, 15)
        val taskId = environment.tasks.save(
            CareTask(
                petId = petId,
                name = "Daily medicine",
                category = TaskCategory.MEDICATION.name,
                frequency = Frequency.DAILY.name,
                startDateEpochDay = today.toEpochDay(),
                timeMinutes = 8 * 60
            )
        )

        val staleBeforeComplete = environment.tasks.getTask(taskId)!!
        assertTrue(environment.tasks.setCompleted(staleBeforeComplete, true, today))
        environment.tasks.save(staleBeforeComplete.copy(name = "Edited from stale screen"))
        assertEquals(
            today.toEpochDay(),
            environment.tasks.getTask(taskId)!!.lastCompletedEpochDay
        )

        val staleAfterComplete = environment.tasks.getTask(taskId)!!
        assertEquals(1, environment.tasks.resetToday(owner.id, today))
        environment.tasks.save(staleAfterComplete.copy(name = "Edited after reset"))
        assertNull(environment.tasks.getTask(taskId)!!.lastCompletedEpochDay)
    }

    @Test
    fun `stale edit changing recurrence clears current completion`() = runBlocking {
        val owner = environment.auth.register(
            "Schedule owner",
            "task-schedule-consistency@example.com",
            "secret1"
        ).getOrThrow()
        val petId = environment.pets.save(
            Pet(ownerId = owner.id, name = "Schedule pet", species = Species.CAT.name)
        )
        val today = LocalDate.of(2026, 9, 15)
        val taskId = environment.tasks.save(
            CareTask(
                petId = petId,
                name = "Daily feeding",
                category = TaskCategory.FEEDING.name,
                frequency = Frequency.DAILY.name,
                startDateEpochDay = today.toEpochDay(),
                timeMinutes = 9 * 60
            )
        )

        val stale = environment.tasks.getTask(taskId)!!
        assertTrue(environment.tasks.setCompleted(stale, true, today))
        environment.tasks.save(
            stale.copy(
                frequency = Frequency.WEEKLY.name,
                name = "Weekly feeding"
            )
        )

        val saved = environment.tasks.getTask(taskId)!!
        assertEquals(Frequency.WEEKLY.name, saved.frequency)
        assertNull(saved.lastCompletedEpochDay)
    }
}