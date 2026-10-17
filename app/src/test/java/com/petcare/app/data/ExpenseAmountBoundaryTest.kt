package com.petcare.app.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.petcare.app.data.repository.RepositoryAccessException
import com.petcare.app.model.Expense
import com.petcare.app.model.ExpenseCategory
import com.petcare.app.util.ExpenseAmounts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ExpenseAmountBoundaryTest {
    private lateinit var environment: TestEnvironment
    private val invalidAmounts = listOf(
        Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
        "1e309".toDouble(), 0.0, -0.0, -1.0
    )

    @Before
    fun setUp() {
        environment = TestEnvironment(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() = environment.close()

    @Test
    fun `repository rejects non-finite and non-positive inserts before SQLite`() = runBlocking {
        val owner = environment.auth.register("Owner", "amount-insert@test.local", "secret1").getOrThrow()
        for (amount in invalidAmounts) {
            val error = runCatching {
                environment.expenses.save(Expense(ownerId = owner.id, amount = amount, category = ExpenseCategory.OTHER.name, description = "Invalid"))
            }.exceptionOrNull()
            assertTrue("Expected validation for $amount, got $error", error is IllegalArgumentException)
            assertEquals(ExpenseAmounts.ERROR, error?.message)
            assertTrue(environment.expenses.observeExpenses(owner.id).first().isEmpty())
            assertNull(environment.expenses.observeTotal(owner.id).first())
        }
    }

    @Test
    fun `invalid edits preserve the saved expense and finite total`() = runBlocking {
        val owner = environment.auth.register("Owner", "amount-edit@test.local", "secret1").getOrThrow()
        val id = environment.expenses.save(Expense(ownerId = owner.id, amount = 12.50, category = ExpenseCategory.FOOD.name, description = "Food"))
        val original = environment.expenses.getExpense(id)!!
        for (amount in invalidAmounts) {
            val error = runCatching { environment.expenses.save(original.copy(amount = amount)) }.exceptionOrNull()
            assertTrue("Expected validation for $amount", error is IllegalArgumentException)
            assertEquals(ExpenseAmounts.ERROR, error?.message)
            assertEquals(original, environment.expenses.getExpense(id))
            assertEquals(12.50, environment.expenses.observeTotal(owner.id).first()!!, 0.0)
        }
        environment.expenses.save(original.copy(amount = 27.75))
        assertEquals(27.75, environment.expenses.observeTotal(owner.id).first()!!, 0.0)
    }

    @Test
    fun `valid numeric extremes retain the finite positive contract`() = runBlocking {
        val owner = environment.auth.register("Owner", "amount-finite@test.local", "secret1").getOrThrow()
        for (amount in listOf(Double.MIN_VALUE, 0.01, 1e100, Double.MAX_VALUE)) {
            val id = environment.expenses.save(Expense(ownerId = owner.id, amount = amount, category = ExpenseCategory.OTHER.name, description = "Finite"))
            val expense = environment.expenses.getExpense(id)!!
            assertEquals(amount, expense.amount, 0.0)
            environment.expenses.delete(expense)
        }
    }

    @Test
    fun `validation does not bypass expense ownership or signed-in checks`() = runBlocking {
        val alice = environment.auth.register("Alice", "amount-alice@test.local", "secret1").getOrThrow()
        val id = environment.expenses.save(Expense(ownerId = alice.id, amount = 5.0, category = ExpenseCategory.OTHER.name, description = "Alice"))
        val original = environment.expenses.getExpense(id)!!
        environment.auth.logout()
        assertTrue(runCatching { environment.expenses.save(original) }.isFailure)
        val bob = environment.auth.register("Bob", "amount-bob@test.local", "secret1").getOrThrow()
        assertTrue(runCatching { environment.expenses.save(original.copy(amount = Double.NaN)) }
            .exceptionOrNull() is RepositoryAccessException)
        assertTrue(runCatching { environment.expenses.save(original.copy(ownerId = bob.id)) }
            .exceptionOrNull() is RepositoryAccessException)
        assertEquals(original, environment.database.expenseDao().findById(id, alice.id))
    }
}