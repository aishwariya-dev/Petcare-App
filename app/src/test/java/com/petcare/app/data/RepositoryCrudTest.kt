package com.petcare.app.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.petcare.app.data.repository.RepositoryAccessException
import com.petcare.app.model.CareTask
import com.petcare.app.model.Expense
import com.petcare.app.model.ExpenseCategory
import com.petcare.app.model.Frequency
import com.petcare.app.model.Pet
import com.petcare.app.model.Species
import com.petcare.app.model.TaskCategory
import com.petcare.app.model.galleryPaths
import com.petcare.app.model.supplyList
import kotlinx.coroutines.flow.first
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
class RepositoryCrudTest {
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
    fun `pet task and expense CRUD persists edits completion and totals`() = runBlocking {
        val owner = environment.auth.register("CRUD Owner", "crud@example.com", "secret1").getOrThrow()
        val today = LocalDate.of(2025, 1, 15)

        val petId = environment.pets.save(
            Pet(
                ownerId = owner.id,
                name = "Milo",
                species = Species.DOG.name,
                breed = "Collie",
                ageYears = 4,
                weightKg = 18.5,
                diet = "Kibble",
                galleryPhotoPaths = """["/images/one.jpg","/images/two.jpg"]"""
            )
        )
        val pet = environment.pets.getPet(petId)!!
        assertEquals(listOf("/images/one.jpg", "/images/two.jpg"), pet.galleryPaths)

        val taskId = environment.tasks.save(
            CareTask(
                petId = petId,
                name = "Morning walk",
                category = TaskCategory.EXERCISE.name,
                frequency = Frequency.DAILY.name,
                startDateEpochDay = today.toEpochDay(),
                timeMinutes = 8 * 60,
                reminderEnabled = true,
                supplies = "Lead, water",
                notes = "Short route"
            )
        )
        val savedTask = environment.tasks.getTask(taskId)!!
        assertTrue(savedTask.reminderEnabled)
        assertEquals(listOf("Lead", "water"), savedTask.supplyList)

        val editedPet = pet.copy(name = "Milo Updated", weightKg = 19.0)
        assertEquals(petId, environment.pets.save(editedPet))
        assertEquals("Milo Updated", environment.pets.getPet(petId)!!.name)

        val editedTask = savedTask.copy(name = "Evening walk", timeMinutes = 20 * 60)
        assertEquals(taskId, environment.tasks.save(editedTask))
        assertEquals("Evening walk", environment.tasks.getTask(taskId)!!.name)

        assertTrue(environment.tasks.setCompleted(editedTask, completed = true, today = today))
        assertEquals(
            today.toEpochDay(),
            environment.database.careTaskDao().findById(taskId, owner.id)!!.lastCompletedEpochDay
        )
        assertEquals(1, environment.tasks.resetToday(owner.id, today))
        assertNull(environment.database.careTaskDao().findById(taskId, owner.id)!!.lastCompletedEpochDay)

        val expenseId = environment.expenses.save(
            Expense(
                ownerId = owner.id,
                petId = petId,
                amount = 12.50,
                category = ExpenseCategory.FOOD.name,
                description = "Food",
                notes = "Monthly bag",
                dateEpochDay = today.toEpochDay()
            )
        )
        val expense = environment.expenses.getExpense(expenseId)!!
        assertEquals(ExpenseCategory.FOOD, expense.categoryType)

        val editedExpense = expense.copy(
            amount = 27.75,
            category = ExpenseCategory.VETERINARY.name,
            description = "Vet check",
            dateEpochDay = today.plusDays(1).toEpochDay()
        )
        assertEquals(expenseId, environment.expenses.save(editedExpense))
        assertEquals(27.75, environment.expenses.getExpense(expenseId)!!.amount, 0.0)
        assertEquals(
            27.75,
            environment.expenses.observeTotal(owner.id).first()!!,
            0.0
        )

        environment.expenses.delete(editedExpense)
        assertNull(environment.expenses.getExpense(expenseId))
        assertEquals(emptyList<Expense>(), environment.expenses.observeExpenses(owner.id).first())
    }

    @Test
    fun `owner predicates prevent cross-account reads writes and attached expenses`() = runBlocking {
        val alice = environment.auth.register("Alice", "alice-crud@example.com", "secret1").getOrThrow()
        val petId = environment.pets.save(
            Pet(ownerId = alice.id, name = "Alice Pet", species = Species.CAT.name)
        )
        val taskId = environment.tasks.save(
            CareTask(
                petId = petId,
                name = "Feed",
                category = TaskCategory.FEEDING.name,
                frequency = Frequency.DAILY.name,
                startDateEpochDay = LocalDate.now().toEpochDay(),
                timeMinutes = 8 * 60
            )
        )
        val expenseId = environment.expenses.save(
            Expense(
                ownerId = alice.id,
                petId = petId,
                amount = 4.0,
                category = ExpenseCategory.FOOD.name,
                description = "Treat"
            )
        )
        val alicePet = environment.pets.getPet(petId)!!
        val aliceTask = environment.tasks.getTask(taskId)!!
        val aliceExpense = environment.expenses.getExpense(expenseId)!!

        environment.auth.logout()
        val bob = environment.auth.register("Bob", "bob-crud@example.com", "secret1").getOrThrow()
        assertNull(environment.pets.getPet(petId))
        assertNull(environment.tasks.getTask(taskId))
        assertNull(environment.expenses.getExpense(expenseId))
        assertFalse(environment.tasks.setCompleted(aliceTask, true))
        assertFalse(environment.tasks.delete(aliceTask))

        assertTrue(
            runCatching {
                environment.pets.save(alicePet.copy(ownerId = bob.id, name = "Hijacked"))
            }.exceptionOrNull() is RepositoryAccessException
        )
        assertTrue(
            runCatching {
                environment.expenses.save(aliceExpense.copy(ownerId = bob.id))
            }.exceptionOrNull() is RepositoryAccessException
        )
        assertTrue(
            runCatching {
                environment.expenses.save(
                    Expense(
                        ownerId = bob.id,
                        petId = petId,
                        amount = 99.0,
                        category = ExpenseCategory.OTHER.name,
                        description = "Wrong pet"
                    )
                )
            }.exceptionOrNull() is RepositoryAccessException
        )
    }

    @Test
    fun `deleting an owner cascades pets tasks and expenses`() = runBlocking {
        val owner = environment.auth.register("Cascade", "cascade@example.com", "secret1").getOrThrow()
        val petId = environment.pets.save(
            Pet(ownerId = owner.id, name = "Cascade Pet", species = Species.DOG.name)
        )
        val taskId = environment.tasks.save(
            CareTask(
                petId = petId,
                name = "Care",
                category = TaskCategory.FEEDING.name,
                frequency = Frequency.ONCE.name,
                startDateEpochDay = LocalDate.now().toEpochDay(),
                timeMinutes = 9 * 60
            )
        )
        val expenseId = environment.expenses.save(
            Expense(
                ownerId = owner.id,
                petId = petId,
                amount = 1.0,
                category = ExpenseCategory.OTHER.name,
                description = "Cascade"
            )
        )

        environment.database.openHelper.writableDatabase.execSQL(
            "DELETE FROM users WHERE id = ?",
            arrayOf(owner.id)
        )

        assertNull(environment.database.petDao().findById(petId, owner.id))
        assertNull(environment.database.careTaskDao().findById(taskId, owner.id))
        assertNull(environment.database.expenseDao().findById(expenseId, owner.id))
    }

    @Test
    fun `editing missing rows reports not-found for every CRUD repository`() = runBlocking {
        val owner = environment.auth.register("Missing", "missing@example.com", "secret1").getOrThrow()
        val petId = environment.pets.save(
            Pet(ownerId = owner.id, name = "Existing", species = Species.DOG.name)
        )

        val petFailure = runCatching {
            environment.pets.save(
                Pet(id = 404, ownerId = owner.id, name = "Missing", species = Species.DOG.name)
            )
        }.exceptionOrNull()
        val taskFailure = runCatching {
            environment.tasks.save(
                CareTask(
                    id = 405,
                    petId = petId,
                    name = "Missing",
                    category = TaskCategory.OTHER.name,
                    frequency = Frequency.ONCE.name,
                    startDateEpochDay = LocalDate.now().toEpochDay(),
                    timeMinutes = 60
                )
            )
        }.exceptionOrNull()
        val expenseFailure = runCatching {
            environment.expenses.save(
                Expense(
                    id = 406,
                    ownerId = owner.id,
                    amount = 1.0,
                    category = ExpenseCategory.OTHER.name,
                    description = "Missing"
                )
            )
        }.exceptionOrNull()

        assertEquals("This pet is no longer available.", petFailure?.message)
        assertEquals("This task is no longer available.", taskFailure?.message)
        assertEquals("This expense is no longer available.", expenseFailure?.message)
    }
}