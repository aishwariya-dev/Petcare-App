package com.petcare.app.util

import com.petcare.app.model.CareTask
import com.petcare.app.model.Frequency
import com.petcare.app.model.Pet
import com.petcare.app.model.TaskCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DelegationTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 15)
    private val pet = Pet(id = 1, ownerId = 1, name = "Max", species = "DOG", breed = "Golden Retriever", diet = "Dry food", allergies = "Chicken")

    private fun task(name: String, category: TaskCategory, frequency: Frequency = Frequency.DAILY, time: Int = 8 * 60, completed: Boolean = false) =
        CareTask(
            petId = 1, name = name, category = category.name, frequency = frequency.name,
            startDateEpochDay = today.toEpochDay(), timeMinutes = time,
            lastCompletedEpochDay = if (completed) today.toEpochDay() else null
        )

    @Test
    fun `options round-trip through the compact encoding`() {
        val options = DelegationOptions(includeChecklist = true, includeFeeding = false, includeMedication = true, includeNotes = false)
        assertEquals("1010", options.encode())
        assertEquals(options, DelegationOptions.decode("1010"))
        assertEquals(DelegationOptions(), DelegationOptions.decode(null))
        assertFalse(DelegationOptions(false, false, false, false).hasSelection)
    }

    @Test
    fun `message contains only the selected sections`() {
        val tasks = listOf(
            task("Breakfast", TaskCategory.FEEDING, time = 7 * 60, completed = true),
            task("Heart tablet", TaskCategory.MEDICATION, time = 9 * 60)
        )
        val message = DelegationMessageBuilder.build(pet, tasks, DelegationOptions(includeChecklist = true, includeFeeding = false, includeMedication = false, includeNotes = false), today)

        assertTrue(message.contains("TODAY'S CHECKLIST"))
        assertTrue(message.contains("${DateTimeFormats.time(7 * 60)} Breakfast (done)"))
        assertTrue(message.contains("${DateTimeFormats.time(9 * 60)} Heart tablet"))
        assertFalse(message.contains("FEEDING"))
        assertFalse(message.contains("MEDICATION"))
        assertFalse(message.contains("NOTES"))
        assertTrue(message.startsWith("PetCare - Max's care plan"))
    }

    @Test
    fun `monthly medication is not described as weekly in an SMS`() {
        val message = DelegationMessageBuilder.build(
            pet,
            listOf(task("Flea treatment", TaskCategory.MEDICATION, Frequency.MONTHLY)),
            DelegationOptions(includeChecklist = false, includeFeeding = false, includeMedication = true, includeNotes = false),
            today
        )
        assertTrue(message.contains("monthly"))
        assertFalse(message.contains("weekly"))
    }

    @Test
    fun `notes and feeding sections include pet details`() {
        val message = DelegationMessageBuilder.build(pet, emptyList(), DelegationOptions(includeChecklist = false, includeFeeding = true, includeMedication = false, includeNotes = true), today)
        assertTrue(message.contains("No feeding schedule set."))
        assertTrue(message.contains("Diet: Dry food"))
        assertTrue(message.contains("Allergies: Chicken"))
    }
}
