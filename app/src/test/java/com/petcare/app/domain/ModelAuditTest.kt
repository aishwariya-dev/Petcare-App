package com.petcare.app.domain

import android.app.Application
import com.petcare.app.model.CareTask
import com.petcare.app.model.Expense
import com.petcare.app.model.ExpenseCategory
import com.petcare.app.model.Frequency
import com.petcare.app.model.LocationType
import com.petcare.app.model.Pet
import com.petcare.app.model.PetLocation
import com.petcare.app.model.PetPhotoPaths
import com.petcare.app.model.Species
import com.petcare.app.model.TaskCategory
import com.petcare.app.model.ThemeMode
import com.petcare.app.model.categoryType
import com.petcare.app.model.frequencyType
import com.petcare.app.model.galleryPaths
import com.petcare.app.model.speciesType
import com.petcare.app.model.startDate
import com.petcare.app.model.subtitle
import com.petcare.app.model.supplyList
import com.petcare.app.model.time
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ModelAuditTest {

    @Test
    fun `persisted enum names resolve and unknown values use safe fallbacks`() {
        assertEquals(Species.CAT, Species.from("CAT"))
        assertEquals(Species.OTHER, Species.from(null))
        assertEquals(Species.OTHER, Species.from("cat"))
        assertEquals(Species.OTHER, Species.from("removed-species"))

        assertEquals(TaskCategory.MEDICATION, TaskCategory.from("MEDICATION"))
        assertEquals(TaskCategory.OTHER, TaskCategory.from("unknown-category"))
        assertEquals(Frequency.MONTHLY, Frequency.from("MONTHLY"))
        assertEquals(Frequency.ONCE, Frequency.from("legacy-frequency"))
        assertEquals(ThemeMode.DARK, ThemeMode.from("DARK"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.from(null))
        assertEquals(ExpenseCategory.VETERINARY, ExpenseCategory.from("VETERINARY"))
        assertEquals(ExpenseCategory.OTHER, ExpenseCategory.from("unknown-expense"))
        assertEquals(LocationType.PARK, LocationType.from("PARK"))
        assertEquals(LocationType.OTHER, LocationType.from("unknown-location"))
    }

    @Test
    fun `enum labels and medical classification remain meaningful to callers`() {
        assertEquals("Dog", Species.DOG.label)
        assertEquals("Health check", TaskCategory.HEALTH_CHECK.label)
        assertTrue(TaskCategory.HEALTH_CHECK.isMedical)
        assertTrue(TaskCategory.VACCINATION.isMedical)
        assertFalse(TaskCategory.GROOMING.isMedical)
        assertEquals("Every month", Frequency.MONTHLY.description)
        assertEquals("Food & Treats", ExpenseCategory.FOOD.label)
        assertEquals("Veterinary Clinic", LocationType.VETERINARY.label)
    }

    @Test
    fun `pet and task derived properties decode persisted values`() {
        val pet = Pet(
            ownerId = 2,
            name = "Miso",
            species = "CAT",
            breed = "",
            galleryPhotoPaths = """["/photos/miso.jpg","/photos/miso.jpg","  ","/photos/second.jpg"]"""
        )
        assertEquals(Species.CAT, pet.speciesType)
        assertEquals("Cat", pet.subtitle)
        assertEquals(
            listOf("/photos/miso.jpg", "/photos/second.jpg"),
            pet.galleryPaths
        )

        val task = CareTask(
            petId = pet.id,
            name = "Evening medicine",
            category = "MEDICATION",
            frequency = "MONTHLY",
            startDateEpochDay = LocalDate.of(2024, 2, 29).toEpochDay(),
            timeMinutes = 23 * 60 + 45,
            supplies = " pill box, water\n"
        )
        assertEquals(TaskCategory.MEDICATION, task.categoryType)
        assertEquals(Frequency.MONTHLY, task.frequencyType)
        assertEquals(LocalDate.of(2024, 2, 29), task.startDate)
        assertEquals(LocalTime.of(23, 45), task.time)
        assertEquals(listOf("pill box", "water"), task.supplyList)

        val legacyTask = task.copy(category = "removed", frequency = "removed")
        assertEquals(TaskCategory.OTHER, legacyTask.categoryType)
        assertEquals(Frequency.ONCE, legacyTask.frequencyType)
    }

    @Test
    fun `pet gallery codec trims duplicates and tolerates corrupt persisted JSON`() {
        val encoded = PetPhotoPaths.encode(
            listOf(" /a.jpg ", "", "/a.jpg", "/b.jpg")
        )
        // Android's JSON implementation may escape "/" in the serialized
        // representation. The contract is the decoded paths, not byte-level
        // JSON spelling.
        assertEquals(
            listOf("/a.jpg", "/b.jpg"),
            PetPhotoPaths.decode(encoded)
        )
        assertEquals(emptyList<String>(), PetPhotoPaths.decode("{not-json"))
        assertEquals(emptyList<String>(), PetPhotoPaths.decode(null))
        assertEquals(
            listOf("/a.jpg", "/b.jpg"),
            PetPhotoPaths.decode("""[" /a.jpg ", "/b.jpg", "/a.jpg"]""")
        )
    }

    @Test
    fun `expense and location derived values reflect persisted boundaries`() {
        val expense = Expense(
            ownerId = 2,
            amount = 19.95,
            category = "VETERINARY",
            dateEpochDay = LocalDate.of(2024, 2, 29).toEpochDay()
        )
        assertEquals(LocalDate.of(2024, 2, 29), expense.date)
        assertEquals(ExpenseCategory.VETERINARY, expense.categoryType)
        assertEquals(ExpenseCategory.OTHER, expense.copy(category = "retired").categoryType)

        val location = PetLocation(
            ownerId = 2,
            name = "Miso Vet",
            type = "VETERINARY",
            latitude = 51.5074,
            longitude = -0.1278
        )
        assertEquals(LocationType.VETERINARY, location.locationType)
        assertTrue(location.hasCoordinates)
        assertFalse(location.copy(longitude = null).hasCoordinates)
        assertEquals(LocationType.OTHER, location.copy(type = "retired").locationType)
    }
}