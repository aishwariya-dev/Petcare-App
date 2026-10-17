package com.petcare.app.reminder

import com.petcare.app.model.CareTask
import com.petcare.app.model.Pet
import com.petcare.app.model.Frequency
import com.petcare.app.model.TaskCategory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class ReminderContentTest {
    private val originalLocale = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `notification and SMS builders expose exact production preview content`() {
        Locale.setDefault(Locale.US)
        val task = CareTask(
            petId = 1,
            name = "Give medication",
            category = TaskCategory.MEDICATION.name,
            frequency = Frequency.DAILY.name,
            startDateEpochDay = 0,
            timeMinutes = 9 * 60 + 5,
            notes = "With food",
            smsRecipient = "+1 555 0100"
        )

        assertEquals(
            ReminderNotificationContent(
                title = "Care reminder",
                body = "9:05 AM — Give medication — With food",
                time = "9:05 AM"
            ),
            ReminderNotification.content(task)
        )
        val pet = Pet(ownerId = 1, name = "Milo", species = "DOG", breed = "Beagle", ageYears = 4)
        val today = LocalDate.of(2026, 9, 21)
        assertEquals(
            AutomaticSmsPayload(
                recipient = "+1 555 0100",
                message = "PetCare: Milo (Beagle, 4 yrs)\n" +
                    "Medication - Give medication\n" +
                    "Today at 9:05 AM\n" +
                    "Note: With food\n" +
                    "Sent from PetCare"
            ),
            automaticSmsPayload(task, pet, today, today)
        )
    }
}