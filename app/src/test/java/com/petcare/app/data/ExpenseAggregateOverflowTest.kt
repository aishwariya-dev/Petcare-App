package com.petcare.app.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.petcare.app.model.Expense
import com.petcare.app.model.ExpenseCategory
import com.petcare.app.util.ExpenseAmounts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ExpenseAggregateOverflowTest {
    private lateinit var environment: TestEnvironment

    @Before
    fun setUp() {
        environment = TestEnvironment(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() {
        environment.close()
    }

    @Test
    fun `finite amounts that would overflow aggregate reject insert and edit`() = runBlocking {
        val owner = environment.auth.register(
            "Expense owner",
            "expense-total-consistency@example.com",
            "secret1"
        ).getOrThrow()
        val halfMax = Double.MAX_VALUE / 2.0
        val firstId = environment.expenses.save(
            Expense(
                ownerId = owner.id,
                amount = halfMax,
                category = ExpenseCategory.OTHER.name,
                description = "First large expense"
            )
        )
        val secondId = environment.expenses.save(
            Expense(
                ownerId = owner.id,
                amount = halfMax,
                category = ExpenseCategory.OTHER.name,
                description = "Second large expense"
            )
        )
        assertEquals(Double.MAX_VALUE, environment.expenses.observeTotal(owner.id).first()!!, 0.0)

        val third = Expense(
            ownerId = owner.id,
            amount = halfMax,
            category = ExpenseCategory.OTHER.name,
            description = "Overflowing third expense"
        )
        val insertError = runCatching { environment.expenses.save(third) }.exceptionOrNull()
        assertTrue(insertError is IllegalArgumentException)
        assertEquals(ExpenseAmounts.ERROR, insertError?.message)

        val editError = runCatching {
            environment.expenses.save(
                environment.expenses.getExpense(firstId)!!.copy(amount = Double.MAX_VALUE)
            )
        }.exceptionOrNull()
        assertTrue(editError is IllegalArgumentException)
        assertEquals(ExpenseAmounts.ERROR, editError?.message)
        assertEquals(halfMax, environment.expenses.getExpense(firstId)!!.amount, 0.0)
        assertEquals(halfMax, environment.expenses.getExpense(secondId)!!.amount, 0.0)
        assertEquals(2, environment.expenses.observeExpenses(owner.id).first().size)
        assertEquals(Double.MAX_VALUE, environment.expenses.observeTotal(owner.id).first()!!, 0.0)
    }
}