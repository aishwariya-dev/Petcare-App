package com.petcare.app.reminder

import com.petcare.app.model.CareTask
import com.petcare.app.model.Frequency
import com.petcare.app.model.Pet
import com.petcare.app.model.TaskCategory
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskSmsTemplateTest {
    private val today: LocalDate = LocalDate.of(2026, 9, 21)
    private val pet = Pet(ownerId = 1, name = "Milo", species = "DOG", breed = "Beagle", ageYears = 4)

    private fun task(notes: String = "With food", supplies: String = "Tablets", name: String = "Give medication") = CareTask(
        petId = 1,
        name = name,
        category = TaskCategory.MEDICATION.name,
        frequency = Frequency.DAILY.name,
        startDateEpochDay = today.toEpochDay(),
        timeMinutes = 9 * 60 + 5,
        notes = notes,
        supplies = supplies,
        smsRecipient = "+1 555 0100"
    )

    @Test
    fun `full message lists pet, task, time, supplies and note`() {
        val text = TaskSmsTemplate.build(task(), pet, today, today, Locale.US)
        assertEquals(
            "PetCare: Milo (Beagle, 4 yrs)\nMedication - Give medication\nToday at 9:05 AM\n" +
                "Needs: Tablets\nNote: With food\nSent from PetCare",
            text
        )
    }

    @Test
    fun `blank fields are left out and species stands in for a missing breed`() {
        val plainPet = pet.copy(breed = "", ageYears = null)
        val text = TaskSmsTemplate.build(task(notes = "", supplies = ""), plainPet, today, today, Locale.US)
        assertEquals("PetCare: Milo (Dog)\nMedication - Give medication\nToday at 9:05 AM\nSent from PetCare", text)
    }

    @Test
    fun `day wording is today, tomorrow or a short date`() {
        assertTrue(TaskSmsTemplate.build(task(), pet, today.plusDays(1), today, Locale.US).contains("\nTomorrow at "))
        assertTrue(TaskSmsTemplate.build(task(), pet, today.plusDays(5), today, Locale.US).contains("\n26 Sep at "))
    }

    @Test
    fun `an over-long note is shortened and nothing else is dropped`() {
        val text = TaskSmsTemplate.build(task(notes = "x".repeat(600)), pet, today, today, Locale.US)
        assertTrue(SmsSegments.isGsm7(text))
        assertEquals(TaskSmsTemplate.MAX_PARTS, SmsSegments.parts(text))
        assertTrue(text.startsWith("PetCare: Milo (Beagle, 4 yrs)\nMedication - Give medication\nToday at 9:05 AM\nNeeds: Tablets\nNote: x"))
        assertTrue(text.endsWith("...\nSent from PetCare"))
    }

    @Test
    fun `typographic punctuation is normalised so the text stays in the cheap GSM alphabet`() {
        val fancy = task(notes = "Vet’s “special” diet – no treats…", supplies = "Tablets\u00A0(2)")
        val text = TaskSmsTemplate.build(fancy, pet, today, today, Locale.US)
        assertTrue(text, SmsSegments.isGsm7(text))
        assertTrue(text, text.contains("Note: Vet's \"special\" diet - no treats..."))
        assertTrue(text, text.contains("Needs: Tablets (2)"))
    }

    @Test
    fun `non-Latin text is capped by UCS-2 parts rather than by GSM characters`() {
        val nepali = task(notes = "औषधि खानापछि दिनुहोस् ".repeat(40))
        val text = TaskSmsTemplate.build(nepali, pet, today, today, Locale.US)
        assertFalse(SmsSegments.isGsm7(text))
        assertTrue(text.length.toString(), text.length <= 3 * 67)
        assertEquals(TaskSmsTemplate.MAX_PARTS, SmsSegments.parts(text))
        assertTrue(text.startsWith("PetCare: Milo (Beagle, 4 yrs)\nMedication - Give medication\nToday at 9:05 AM\nNeeds: Tablets\nNote: औषधि"))
        assertTrue(text.endsWith("...\nSent from PetCare"))
    }

    @Test
    fun `supplies shrink only after the note is gone and long names still fit`() {
        val text = TaskSmsTemplate.build(task(notes = "y".repeat(50), supplies = "z".repeat(600)), pet, today, today, Locale.US)
        assertEquals(TaskSmsTemplate.MAX_PARTS, SmsSegments.parts(text))
        assertTrue(text, text.contains("Needs: zzz"))
        assertFalse(text, text.contains("Note:"))

        val longNames = task(name = "क".repeat(120))
        val hugePet = pet.copy(name = "ख".repeat(120))
        val compact = TaskSmsTemplate.build(longNames, hugePet, today, today, Locale.US)
        assertTrue(compact, TaskSmsTemplate.fits(compact))
        assertTrue(compact, compact.startsWith("PetCare: ख"))
    }
}
