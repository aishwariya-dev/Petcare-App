package com.petcare.app.ui

import com.petcare.app.model.CareTask
import com.petcare.app.model.Frequency
import com.petcare.app.model.Pet
import com.petcare.app.model.TaskCategory
import com.petcare.app.ui.screens.dashboard.PetToday
import com.petcare.app.ui.screens.dashboard.TodayProgress
import com.petcare.app.ui.screens.dashboard.greeting
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayProgressTest {
    private fun pet(id: Long, name: String) = Pet(id = id, ownerId = 1, name = name, species = "DOG")
    private fun task(id: Long, petId: Long) = CareTask(
        id = id, petId = petId, name = "Task $id", category = TaskCategory.FEEDING.name,
        frequency = Frequency.DAILY.name, startDateEpochDay = 0, timeMinutes = 480
    )

    @Test
    fun `sums per-pet progress and skips pets without tasks today`() {
        val progress = TodayProgress.from(
            listOf(
                PetToday(pet(1, "Milo"), listOf(task(1, 1), task(2, 1)), completedIds = setOf(1)),
                PetToday(pet(2, "Luna"), listOf(task(3, 2)), completedIds = setOf(3)),
                PetToday(pet(3, "Rex"), emptyList(), completedIds = emptySet())
            )
        )
        assertEquals(2, progress.done)
        assertEquals(3, progress.total)
        assertEquals(1, progress.remaining)
        assertEquals(2, progress.perPet.size)
        assertEquals(2f / 3f, progress.fraction, 0.0001f)
        assertFalse(progress.allDone)
    }

    @Test
    fun `empty day is zero without dividing by zero`() {
        val progress = TodayProgress.from(emptyList())
        assertEquals(0f, progress.fraction, 0f)
        assertFalse(progress.allDone)
        assertTrue(TodayProgress(2, 2, emptyList()).allDone)
    }

    @Test
    fun `greeting follows the time of day and uses the first name`() {
        assertEquals("Good morning, Nitesh", greeting("Nitesh Sharma", LocalTime.of(8, 0)))
        assertEquals("Good afternoon, Nitesh", greeting("Nitesh Sharma", LocalTime.of(12, 0)))
        assertEquals("Good evening, Nitesh", greeting(" Nitesh ", LocalTime.of(17, 0)))
    }
}
