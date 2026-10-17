package com.petcare.app.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.petcare.app.domain.TaskSchedule
import com.petcare.app.model.CareTask
import com.petcare.app.model.Frequency
import com.petcare.app.model.Pet
import com.petcare.app.model.Species
import com.petcare.app.model.TaskCategory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class TaskCompletionConsistencyRepositoryTest {
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
    fun `completion is rejected before the requested one-off occurrence`() = runBlocking {
        val owner = environment.auth.register(
            "Future owner",
            "future-completion@example.com",
            "secret1"
        ).getOrThrow()
        val petId = environment.pets.save(
            Pet(ownerId = owner.id, name = "Future pet", species = Species.DOG.name)
        )
        val today = LocalDate.of(2026, 9, 15)
        val due = today.plusDays(1)
        val taskId = environment.tasks.save(
            CareTask(
                petId = petId,
                name = "Future one-off",
                category = TaskCategory.MEDICATION.name,
                frequency = Frequency.ONCE.name,
                startDateEpochDay = due.toEpochDay(),
                timeMinutes = 8 * 60
            )
        )

        val saved = environment.tasks.getTask(taskId)!!
        assertFalse(environment.tasks.setCompleted(saved, completed = true, today = today))
        assertNull(environment.tasks.getTask(taskId)!!.lastCompletedEpochDay)
        assertEquals(due, TaskSchedule.nextOccurrenceAfter(environment.tasks.getTask(taskId)!!, today))
    }

    @Test
    fun `completion follows actual recurrence and clearing remains available for undo`() = runBlocking {
        val owner = environment.auth.register(
            "Occurrence owner",
            "occurrence-completion@example.com",
            "secret1"
        ).getOrThrow()
        val petId = environment.pets.save(
            Pet(ownerId = owner.id, name = "Occurrence pet", species = Species.CAT.name)
        )
        val today = LocalDate.of(2026, 9, 15)
        val taskId = environment.tasks.save(
            CareTask(
                petId = petId,
                name = "Overdue one-off",
                category = TaskCategory.FEEDING.name,
                frequency = Frequency.ONCE.name,
                startDateEpochDay = today.minusDays(2).toEpochDay(),
                timeMinutes = 9 * 60
            )
        )

        val saved = environment.tasks.getTask(taskId)!!
        assertTrue(TaskSchedule.occursOn(saved, today))
        assertTrue(environment.tasks.setCompleted(saved, completed = true, today = today))
        val completed = environment.tasks.getTask(taskId)!!
        assertEquals(today.toEpochDay(), completed.lastCompletedEpochDay)
        // The completed overdue one-off is no longer a pending checklist row,
        // but detail Undo must still be able to clear its historical mark.
        assertFalse(TaskSchedule.occursOn(completed, today))
        assertTrue(environment.tasks.setCompleted(completed, completed = false, today = today))
        assertNull(environment.tasks.getTask(taskId)!!.lastCompletedEpochDay)
    }

    @Test
    fun `weekly and monthly off-days reject completion while due-days accept it`() = runBlocking {
        val owner = environment.auth.register(
            "Recurrence owner",
            "recurrence-completion@example.com",
            "secret1"
        ).getOrThrow()
        val petId = environment.pets.save(
            Pet(ownerId = owner.id, name = "Recurrence pet", species = Species.CAT.name)
        )

        val weeklyDue = LocalDate.of(2026, 9, 15) // Tuesday.
        val weeklyId = environment.tasks.save(
            CareTask(
                petId = petId,
                name = "Weekly task",
                category = TaskCategory.EXERCISE.name,
                frequency = Frequency.WEEKLY.name,
                startDateEpochDay = weeklyDue.toEpochDay(),
                timeMinutes = 8 * 60
            )
        )
        val weekly = environment.tasks.getTask(weeklyId)!!
        assertFalse(
            environment.tasks.setCompleted(
                weekly,
                completed = true,
                today = weeklyDue.plusDays(1)
            )
        )
        assertNull(environment.tasks.getTask(weeklyId)!!.lastCompletedEpochDay)
        assertTrue(environment.tasks.setCompleted(weekly, completed = true, today = weeklyDue))
        assertEquals(weeklyDue.toEpochDay(), environment.tasks.getTask(weeklyId)!!.lastCompletedEpochDay)

        val monthlyDue = LocalDate.of(2026, 2, 28)
        val monthlyId = environment.tasks.save(
            CareTask(
                petId = petId,
                name = "Monthly task",
                category = TaskCategory.HEALTH_CHECK.name,
                frequency = Frequency.MONTHLY.name,
                startDateEpochDay = LocalDate.of(2026, 1, 31).toEpochDay(),
                timeMinutes = 9 * 60
            )
        )
        val monthly = environment.tasks.getTask(monthlyId)!!
        assertFalse(
            environment.tasks.setCompleted(
                monthly,
                completed = true,
                today = monthlyDue.minusDays(1)
            )
        )
        assertNull(environment.tasks.getTask(monthlyId)!!.lastCompletedEpochDay)
        assertTrue(environment.tasks.setCompleted(monthly, completed = true, today = monthlyDue))
        assertEquals(monthlyDue.toEpochDay(), environment.tasks.getTask(monthlyId)!!.lastCompletedEpochDay)
    }

    @Test
    fun `a forged stale snapshot cannot complete after its stored schedule moves future`() = runBlocking {
        val owner = environment.auth.register(
            "Moved schedule owner",
            "moved-schedule@example.com",
            "secret1"
        ).getOrThrow()
        val petId = environment.pets.save(
            Pet(ownerId = owner.id, name = "Moved schedule pet", species = Species.DOG.name)
        )
        val today = LocalDate.of(2026, 9, 15)
        val taskId = environment.tasks.save(
            CareTask(
                petId = petId,
                name = "Moved task",
                category = TaskCategory.FEEDING.name,
                frequency = Frequency.ONCE.name,
                startDateEpochDay = today.toEpochDay(),
                timeMinutes = 7 * 60
            )
        )
        val stale = environment.tasks.getTask(taskId)!!
        val future = today.plusDays(3)
        environment.tasks.save(stale.copy(startDateEpochDay = future.toEpochDay()))

        assertFalse(environment.tasks.setCompleted(stale, completed = true, today = today))
        assertNull(environment.tasks.getTask(taskId)!!.lastCompletedEpochDay)
        assertEquals(future, TaskSchedule.nextOccurrenceAfter(environment.tasks.getTask(taskId)!!, today))
    }

    @Test
    fun `completion lookup remains isolated from another signed-in account`() = runBlocking {
        val alice = environment.auth.register(
            "Alice",
            "completion-alice@example.com",
            "secret1"
        ).getOrThrow()
        val petId = environment.pets.save(
            Pet(ownerId = alice.id, name = "Alice pet", species = Species.DOG.name)
        )
        val today = LocalDate.of(2026, 9, 15)
        val taskId = environment.tasks.save(
            CareTask(
                petId = petId,
                name = "Alice task",
                category = TaskCategory.FEEDING.name,
                frequency = Frequency.DAILY.name,
                startDateEpochDay = today.toEpochDay(),
                timeMinutes = 8 * 60
            )
        )
        val aliceTask = environment.tasks.getTask(taskId)!!

        environment.auth.logout()
        val bob = environment.auth.register(
            "Bob",
            "completion-bob@example.com",
            "secret1"
        ).getOrThrow()
        assertFalse(environment.tasks.setCompleted(aliceTask, completed = true, today = today))
        assertNull(environment.tasks.getTask(taskId))

        environment.auth.logout()
        environment.auth.login("completion-alice@example.com", "secret1").getOrThrow()
        assertEquals(
            alice.id,
            environment.tasks.getTask(taskId)?.petId?.let { environment.pets.getPet(it)?.ownerId }
        )
        assertNull(environment.tasks.getTask(taskId)?.lastCompletedEpochDay)
        assertTrue(bob.id != alice.id)
    }

    @Test
    fun `repeating completion for an overdue one-off is idempotent`() = runBlocking {
        val owner = environment.auth.register(
            "Idempotent owner",
            "idempotent-completion@example.com",
            "secret1"
        ).getOrThrow()
        val petId = environment.pets.save(
            Pet(ownerId = owner.id, name = "Idempotent pet", species = Species.DOG.name)
        )
        val today = LocalDate.of(2026, 9, 15)
        val taskId = environment.tasks.save(
            CareTask(
                petId = petId,
                name = "Overdue idempotent task",
                category = TaskCategory.MEDICATION.name,
                frequency = Frequency.ONCE.name,
                startDateEpochDay = today.minusDays(1).toEpochDay(),
                timeMinutes = 8 * 60
            )
        )

        val saved = environment.tasks.getTask(taskId)!!
        assertTrue(environment.tasks.setCompleted(saved, completed = true, today = today))
        assertTrue(
            environment.tasks.setCompleted(
                environment.tasks.getTask(taskId)!!,
                completed = true,
                today = today
            )
        )
        assertEquals(today.toEpochDay(), environment.tasks.getTask(taskId)!!.lastCompletedEpochDay)
    }

    @Test
    fun `clearing a historical completion is allowed even when it is no longer scheduled`() = runBlocking {
        val owner = environment.auth.register(
            "Historical owner",
            "historical-completion@example.com",
            "secret1"
        ).getOrThrow()
        val petId = environment.pets.save(
            Pet(ownerId = owner.id, name = "Historical pet", species = Species.DOG.name)
        )
        val today = LocalDate.of(2026, 9, 15)
        val due = today.minusDays(3)
        val taskId = environment.tasks.save(
            CareTask(
                petId = petId,
                name = "Historical one-off",
                category = TaskCategory.HEALTH_CHECK.name,
                frequency = Frequency.ONCE.name,
                startDateEpochDay = due.toEpochDay(),
                timeMinutes = 10 * 60,
                lastCompletedEpochDay = due.toEpochDay()
            )
        )

        val saved = environment.tasks.getTask(taskId)!!
        assertFalse(TaskSchedule.occursOn(saved, today))
        assertTrue(environment.tasks.setCompleted(saved, completed = false, today = today))
        assertNull(environment.tasks.getTask(taskId)!!.lastCompletedEpochDay)
    }
}