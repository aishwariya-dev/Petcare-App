package com.petcare.app.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.petcare.app.data.TestEnvironment
import com.petcare.app.data.awaitCondition
import com.petcare.app.model.Expense
import com.petcare.app.model.ExpenseCategory
import com.petcare.app.navigation.Routes
import com.petcare.app.ui.screens.expenses.ExpenseFormViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ExpenseFormViewModelTest {
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
    fun `amount validation rejects ordinary invalid values`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        environment.auth.register("Expense Validator", "expense-validation@example.com", "secret1")
        val viewModel = ExpenseFormViewModel(
            environment.auth,
            environment.expenses,
            SavedStateHandle()
        )
        try {
            listOf("", "-1", "0", "not-a-number", "-Infinity").forEach { amount ->
                viewModel.onAmountChange(amount)
                viewModel.onDescriptionChange("A valid description")
                viewModel.submit()
                assertNotNull("Expected amount $amount to be rejected", viewModel.state.value.amountError)
                assertEquals(false, viewModel.state.value.isSubmitting)
            }
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `amount validation rejects NaN`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = environment.auth.register(
            "NaN Expense Validator",
            "expense-nan@example.com",
            "secret1"
        ).getOrThrow()
        val viewModel = ExpenseFormViewModel(
            environment.auth,
            environment.expenses,
            SavedStateHandle()
        )
        try {
            viewModel.onAmountChange("NaN")
            viewModel.onDescriptionChange("A valid description")
            viewModel.submit()
            awaitCondition(description = "NaN save attempt settles") {
                !viewModel.state.value.isSubmitting
            }
            assertTrue(
                "NaN must not persist an expense row",
                environment.database.expenseDao().observeForOwner(owner.id).first().isEmpty()
            )
            assertNotNull("NaN requires field-level validation", viewModel.state.value.amountError)
            assertEquals(false, viewModel.state.value.isSubmitting)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `amount validation rejects positive Infinity`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = environment.auth.register(
            "Infinity Expense Validator",
            "expense-infinity@example.com",
            "secret1"
        ).getOrThrow()
        val viewModel = ExpenseFormViewModel(
            environment.auth,
            environment.expenses,
            SavedStateHandle()
        )
        try {
            viewModel.onAmountChange("Infinity")
            viewModel.onDescriptionChange("A valid description")
            viewModel.submit()
            awaitCondition(description = "Infinity save attempt settles") {
                !viewModel.state.value.isSubmitting
            }
            val persisted = environment.database.expenseDao().observeForOwner(owner.id).first()
            assertTrue(
                "Positive Infinity must not persist a non-finite expense amount",
                persisted.none { !it.amount.isFinite() }
            )
            assertNotNull("Positive Infinity requires field-level validation", viewModel.state.value.amountError)
            assertEquals(false, viewModel.state.value.isSubmitting)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `amount validation rejects overflow`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = environment.auth.register(
            "Overflow Expense Validator",
            "expense-overflow@example.com",
            "secret1"
        ).getOrThrow()
        val viewModel = ExpenseFormViewModel(
            environment.auth,
            environment.expenses,
            SavedStateHandle()
        )
        try {
            viewModel.onAmountChange("1e309")
            viewModel.onDescriptionChange("A valid description")
            viewModel.submit()
            awaitCondition(description = "overflow save attempt settles") {
                !viewModel.state.value.isSubmitting
            }
            val persisted = environment.database.expenseDao().observeForOwner(owner.id).first()
            assertTrue(
                "Overflow must not persist a non-finite expense amount",
                persisted.none { !it.amount.isFinite() }
            )
            assertNotNull("Overflow requires field-level validation", viewModel.state.value.amountError)
            assertEquals(false, viewModel.state.value.isSubmitting)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `new and edit expense forms persist amount category description notes and date`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = environment.auth.register("Expense Owner", "expense-form@example.com", "secret1")
            .getOrThrow()
        val create = ExpenseFormViewModel(environment.auth, environment.expenses, SavedStateHandle())
        var edit: ExpenseFormViewModel? = null
        try {
            val date = LocalDate.of(2025, 4, 5)
            create.onAmountChange("12.50")
            create.onCategoryChange(ExpenseCategory.FOOD)
            create.onDescriptionChange("Monthly food")
            create.onNotesChange("Sensitive stomach")
            create.onDateChange(date)
            create.submit()
            awaitCondition(description = "new expense save") { create.state.value.success }

            val expense = environment.database.expenseDao().observeForOwner(owner.id).first()
                .single()
            assertEquals(12.50, expense.amount, 0.0)
            assertEquals(ExpenseCategory.FOOD.name, expense.category)
            assertEquals("Monthly food", expense.description)
            assertEquals("Sensitive stomach", expense.notes)
            assertEquals(date.toEpochDay(), expense.dateEpochDay)

            val editViewModel = ExpenseFormViewModel(
                environment.auth,
                environment.expenses,
                SavedStateHandle(mapOf(Routes.ARG_EXPENSE_ID to expense.id))
            )
            edit = editViewModel
            awaitCondition(description = "expense edit hydration") {
                editViewModel.state.value.amount == "12.5"
            }
            assertEquals(ExpenseCategory.FOOD, editViewModel.state.value.category)
            assertEquals(date, editViewModel.state.value.date)

            editViewModel.onAmountChange("27.75")
            editViewModel.onCategoryChange(ExpenseCategory.VETERINARY)
            editViewModel.onDescriptionChange("Vet check")
            editViewModel.onDateChange(date.plusDays(2))
            editViewModel.submit()
            awaitCondition(description = "edited expense save") { editViewModel.state.value.success }

            val saved = environment.expenses.getExpense(expense.id)!!
            assertEquals(27.75, saved.amount, 0.0)
            assertEquals(ExpenseCategory.VETERINARY.name, saved.category)
            assertEquals("Vet check", saved.description)
            assertEquals(date.plusDays(2).toEpochDay(), saved.dateEpochDay)
        } finally {
            create.viewModelScope.cancel()
            edit?.viewModelScope?.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `editing a missing expense surfaces repository not-found failure`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        environment.auth.register("Missing Expense", "missing-expense@example.com", "secret1")
        val viewModel = ExpenseFormViewModel(
            environment.auth,
            environment.expenses,
            SavedStateHandle(mapOf(Routes.ARG_EXPENSE_ID to 9999L))
        )
        try {
            viewModel.onAmountChange("5")
            viewModel.onDescriptionChange("Missing row")
            viewModel.submit()
            awaitCondition(description = "missing expense form error") {
                viewModel.state.value.formError != null
            }
            assertEquals("This expense is no longer available.", viewModel.state.value.formError)
            assertTrue(!viewModel.state.value.success)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }
}