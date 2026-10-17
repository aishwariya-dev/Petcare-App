package com.petcare.app.data.local

import androidx.room.withTransaction
import com.petcare.app.model.CareTask
import com.petcare.app.model.Expense
import com.petcare.app.model.ExpenseCategory
import com.petcare.app.model.Frequency
import com.petcare.app.model.Pet
import com.petcare.app.model.Species
import com.petcare.app.model.TaskCategory
import com.petcare.app.model.User
import com.petcare.app.util.PasswordHasher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Loads the assignment demo household (Emily, Max, Luna) the first time the
 * database is created, so the app is demonstrable immediately.
 * Runs once and never touches data the user creates afterwards.
 */
object DatabaseSeeder {

    const val DEMO_EMAIL = "emily@petcare.com"
    const val DEMO_PASSWORD = "petcare123"

    private val lock = Mutex()

    /**
     * The whole seed is one Room transaction run under [NonCancellable], so a
     * cancelled coroutine can never leave a half-created household.
     */
    suspend fun seed(db: PetCareDatabase) = lock.withLock {
        withContext(NonCancellable) {
            db.withTransaction { insertHousehold(db) }
        }
    }

    private suspend fun insertHousehold(db: PetCareDatabase) {
        val userDao = db.userDao()
        if (userDao.findByEmail(DEMO_EMAIL) != null) return   // already seeded

        // ── User ────────────────────────────────────────────────────────────────
        val salt = PasswordHasher.newSalt()
        val emilyId = userDao.insert(
            User(
                name = "Emily Carter",
                email = DEMO_EMAIL,
                passwordHash = PasswordHasher.hash(DEMO_PASSWORD, salt),
                passwordSalt = salt,
                phoneNumber = "+44 7700 900000"
            )
        )

        // ── Pets ─────────────────────────────────────────────────────────────────
        val petDao = db.petDao()
        val maxId = petDao.insert(
            Pet(
                ownerId = emilyId,
                name = "Max",
                species = Species.DOG.name,
                breed = "Golden Retriever",
                ageYears = 4,
                weightKg = 30.0,
                diet = "Grain-free kibble, twice daily (AM & PM). Sensitive stomach formula.",
                allergies = "Chicken — avoid all poultry treats, broths and by-products.",
                vaccinations = "Annual rabies booster (Dr. Stewart). Flea & tick chew monthly.",
                favouriteToys = "Rubber tennis ball, plush duck squeaker, rope tug.",
                notes = "Loves outdoor fetch and water play."
            )
        )
        val lunaId = petDao.insert(
            Pet(
                ownerId = emilyId,
                name = "Luna",
                species = Species.CAT.name,
                breed = "Domestic Shorthair",
                ageYears = 2,
                weightKg = 4.5,
                diet = "Wet food in the morning, dry food in the evening.",
                allergies = "",
                vaccinations = "Core vaccines up to date; booster due this month.",
                favouriteToys = "Feather wand, crinkle balls.",
                notes = "Indoor cat. Shy with visitors."
            )
        )

        // ── Care Tasks ────────────────────────────────────────────────────────────
        val today = LocalDate.now().toEpochDay()
        val taskDao = db.careTaskDao()
        listOf(
            CareTask(
                petId = maxId,
                name = "Morning & Evening Meals",
                category = TaskCategory.FEEDING.name,
                frequency = Frequency.DAILY.name,
                startDateEpochDay = today,
                timeMinutes = 7 * 60 + 30,
                supplies = "Grain-free kibble, 1.5 cups per serving",
                notes = "Strictly no chicken."
            ),
            CareTask(
                petId = maxId,
                name = "Daily Walk",
                category = TaskCategory.EXERCISE.name,
                frequency = Frequency.DAILY.name,
                startDateEpochDay = today,
                timeMinutes = 16 * 60 + 30,
                supplies = "Blue harness (hallway hook), poop bags",
                notes = "Afternoon park walk, 30 minutes."
            ),
            CareTask(
                petId = maxId,
                name = "Flea & Tick Treatment",
                category = TaskCategory.MEDICATION.name,
                frequency = Frequency.WEEKLY.name,
                startDateEpochDay = today,
                timeMinutes = 18 * 60,
                supplies = "NexGard chew",
                notes = "Give with food."
            ),
            CareTask(
                petId = lunaId,
                name = "Litter Box Cleaning",
                category = TaskCategory.CLEANING.name,
                frequency = Frequency.DAILY.name,
                startDateEpochDay = today,
                timeMinutes = 18 * 60 + 30,
                supplies = "Scoop, fresh litter",
                notes = "Clean tray fully once a week."
            ),
            CareTask(
                petId = lunaId,
                name = "Vaccination Booster",
                category = TaskCategory.VACCINATION.name,
                frequency = Frequency.ONCE.name,
                startDateEpochDay = today + 5,
                timeMinutes = 10 * 60,
                supplies = "Carrier, vaccination card",
                notes = "Record booster in her health log."
            )
        ).forEach { taskDao.insert(it) }

        // ── Expenses ──────────────────────────────────────────────────────────────
        val expenseDao = db.expenseDao()
        listOf(
            Expense(
                ownerId = emilyId, petId = maxId, amount = 45.00,
                category = ExpenseCategory.FOOD.name,
                description = "Premium dog food (grain-free, 10 kg)",
                dateEpochDay = today - 7
            ),
            Expense(
                ownerId = emilyId, petId = lunaId, amount = 85.00,
                category = ExpenseCategory.VETERINARY.name,
                description = "Luna annual health check-up",
                dateEpochDay = today - 14
            ),
            Expense(
                ownerId = emilyId, petId = maxId, amount = 35.00,
                category = ExpenseCategory.GROOMING.name,
                description = "Max grooming session",
                notes = "Bath, trim and nail clip.",
                dateEpochDay = today - 21
            ),
            Expense(
                ownerId = emilyId, amount = 25.00,
                category = ExpenseCategory.MEDICATION.name,
                description = "NexGard flea & tick treatment (3-month pack)",
                dateEpochDay = today - 3
            ),
            Expense(
                ownerId = emilyId, petId = maxId, amount = 18.50,
                category = ExpenseCategory.TOYS.name,
                description = "New rubber ball and rope toy",
                dateEpochDay = today
            )
        ).forEach { expenseDao.insert(it) }

    }
}
