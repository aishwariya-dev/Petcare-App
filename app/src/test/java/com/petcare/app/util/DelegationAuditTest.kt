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
import java.util.Locale

class DelegationAuditTest {

    private val today = LocalDate.of(2026, 9, 15)
    private val pet = Pet(
        id = 1,
        ownerId = 4,
        name = "Luna",
        species = "DOG",
        diet = "Wet food",
        allergies = "Chicken",
        favouriteToys = "Blue ball",
        notes = "Keep her water bowl full."
    )

    private fun task(
        name: String,
        category: TaskCategory,
        frequency: Frequency,
        timeMinutes: Int,
        supplies: String = "",
        notes: String = "",
        completed: Boolean = false
    ) = CareTask(
        petId = pet.id,
        name = name,
        category = category.name,
        frequency = frequency.name,
        startDateEpochDay = today.toEpochDay(),
        timeMinutes = timeMinutes,
        supplies = supplies,
        notes = notes,
        lastCompletedEpochDay = today.toEpochDay().takeIf { completed }
    )

    @Test
    fun `all sixteen option combinations round trip in documented bit order`() {
        val encodedFixtures = listOf(
            "0000", "0001", "0010", "0011",
            "0100", "0101", "0110", "0111",
            "1000", "1001", "1010", "1011",
            "1100", "1101", "1110", "1111"
        )

        encodedFixtures.forEach { encoded ->
            val options = DelegationOptions(
                includeChecklist = encoded[0] == '1',
                includeFeeding = encoded[1] == '1',
                includeMedication = encoded[2] == '1',
                includeNotes = encoded[3] == '1'
            )
            assertEquals(encoded, options.encode())
            assertEquals(options, DelegationOptions.decode(encoded))
            assertEquals(encoded != "0000", options.hasSelection)
        }
    }

    @Test
    fun `missing option bits are treated as disabled and null restores all defaults`() {
        assertEquals(
            DelegationOptions(
                includeChecklist = true,
                includeFeeding = false,
                includeMedication = false,
                includeNotes = false
            ),
            DelegationOptions.decode("1")
        )
        assertEquals(
            DelegationOptions(
                includeChecklist = true,
                includeFeeding = false,
                includeMedication = true,
                includeNotes = false
            ),
            DelegationOptions.decode("101")
        )
        assertEquals(DelegationOptions(), DelegationOptions.decode(null))
        assertEquals("1010", DelegationOptions.decode("10101").encode())
    }

    @Test
    fun `full care plan text contains independent sections in stable order`() {
        Locale.setDefault(Locale.US)
        val tasks = listOf(
            task(
                "Breakfast",
                TaskCategory.FEEDING,
                Frequency.DAILY,
                7 * 60 + 5,
                supplies = "ceramic bowl",
                notes = "before walk",
                completed = true
            ),
            task(
                "Flea treatment",
                TaskCategory.MEDICATION,
                Frequency.MONTHLY,
                9 * 60,
                supplies = "dropper",
                notes = "with breakfast"
            ),
            task("Long walk", TaskCategory.EXERCISE, Frequency.DAILY, 10 * 60)
        )

        val message = DelegationMessageBuilder.build(
            pet,
            tasks,
            DelegationOptions(),
            today
        )

        assertEquals(
            """
            PetCare - Luna's care plan (15 Sep)
            Luna - Dog

            TODAY'S CHECKLIST
            7:05 AM Breakfast (done)
            9:00 AM Flea treatment
            10:00 AM Long walk

            FEEDING
            Breakfast - daily at 7:05 AM - needs: ceramic bowl (before walk)
            Diet: Wet food

            MEDICATION & HEALTH
            Flea treatment - monthly at 9:00 AM - needs: dropper (with breakfast)

            NOTES
            Allergies: Chicken
            Favourite toys: Blue ball
            Keep her water bowl full.

            Sent from PetCare
            """.trimIndent(),
            message
        )
    }

    @Test
    fun `unselected sections do not leak task or pet details`() {
        val message = DelegationMessageBuilder.build(
            pet,
            listOf(
                task("Breakfast", TaskCategory.FEEDING, Frequency.DAILY, 420),
                task("Tablet", TaskCategory.MEDICATION, Frequency.DAILY, 540)
            ),
            DelegationOptions(
                includeChecklist = false,
                includeFeeding = false,
                includeMedication = false,
                includeNotes = false
            ),
            today
        )

        assertEquals(
            "PetCare - Luna's care plan (15 Sep)\nLuna - Dog\n\nSent from PetCare",
            message
        )
        assertFalse(message.contains("Breakfast"))
        assertFalse(message.contains("Chicken"))
        assertFalse(message.contains("MEDICATION"))
    }

    @Test
    fun `empty selected sections explain that no care data was supplied`() {
        val message = DelegationMessageBuilder.build(
            pet.copy(diet = "", allergies = "", favouriteToys = "", notes = ""),
            emptyList(),
            DelegationOptions(
                includeChecklist = true,
                includeFeeding = true,
                includeMedication = true,
                includeNotes = true
            ),
            today
        )

        assertTrue(message.contains("Nothing scheduled today."))
        assertTrue(message.contains("No feeding schedule set."))
        assertTrue(message.contains("No medication scheduled."))
        assertTrue(message.contains("No extra notes."))
    }
}