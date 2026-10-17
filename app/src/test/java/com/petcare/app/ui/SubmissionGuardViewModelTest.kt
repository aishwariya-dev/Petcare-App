package com.petcare.app.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.petcare.app.data.TestEnvironment
import com.petcare.app.data.awaitCondition
import com.petcare.app.model.Pet
import com.petcare.app.model.Species
import com.petcare.app.ui.screens.auth.LoginViewModel
import com.petcare.app.ui.screens.auth.RegisterViewModel
import com.petcare.app.ui.screens.expenses.ExpenseFormViewModel
import com.petcare.app.ui.screens.tasks.TaskFormViewModel
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SubmissionGuardViewModelTest {
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
    fun `identical invalid submissions remain retryable across all forms`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val login = LoginViewModel(environment.auth)
        val register = RegisterViewModel(environment.auth)
        val expense = ExpenseFormViewModel(
            environment.auth,
            environment.expenses,
            SavedStateHandle()
        )
        val task = TaskFormViewModel(
            environment.auth,
            environment.pets,
            environment.tasks,
            environment.imageStorage,
            SavedStateHandle()
        )
        try {
            login.submit()
            login.submit()
            assertEquals("Email is required", login.state.value.emailError)
            assertEquals("Password is required", login.state.value.passwordError)
            assertFalse(login.state.value.isSubmitting)

            register.submit()
            register.submit()
            assertEquals("Name is required", register.state.value.nameError)
            assertEquals("Email is required", register.state.value.emailError)
            assertEquals("Password is required", register.state.value.passwordError)
            assertEquals("Please confirm your password", register.state.value.confirmError)
            assertFalse(register.state.value.isSubmitting)

            expense.submit()
            expense.submit()
            assertEquals("Amount is required", expense.state.value.amountError)
            assertEquals("Description is required", expense.state.value.descriptionError)
            assertFalse(expense.state.value.isSubmitting)

            task.save()
            task.save()
            assertEquals("Choose a pet", task.state.value.petError)
            assertEquals("Task name is required", task.state.value.nameError)
            assertEquals("Choose a time", task.state.value.timeError)
            assertFalse(task.state.value.isSaving)
        } finally {
            login.viewModelScope.cancel()
            register.viewModelScope.cancel()
            expense.viewModelScope.cancel()
            task.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `valid in-flight submissions are admitted only once`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val loginOwner = environment.auth.register(
            "Guarded Login Owner",
            "guarded-login@example.com",
            "secret1"
        ).getOrThrow()
        environment.auth.logout()
        val login = LoginViewModel(environment.auth)
        val register = RegisterViewModel(environment.auth)
        try {
            login.onEmailChange(loginOwner.email)
            login.onPasswordChange("secret1")
            login.submit()
            assertTrue(login.state.value.isSubmitting)
            login.submit()
            assertTrue(login.state.value.isSubmitting)
            awaitCondition(description = "guarded login") {
                login.state.value.success
            }
            login.submit()
            assertTrue(login.state.value.success)

            register.onNameChange("Guarded User")
            register.onEmailChange("guarded-user@example.com")
            register.onPasswordChange("secret1")
            register.onConfirmChange("secret1")

            register.submit()
            assertTrue(register.state.value.isSubmitting)
            register.submit()
            assertTrue(register.state.value.isSubmitting)

            awaitCondition(description = "guarded registration") {
                register.state.value.success
            }
            register.submit()
            assertTrue(register.state.value.success)
            assertNotNull(
                environment.database.userDao().findByEmail("guarded-user@example.com")
            )
        } finally {
            login.viewModelScope.cancel()
            register.viewModelScope.cancel()
            Dispatchers.resetMain()
        }

        val owner = environment.auth.register(
            "Guarded Expense Owner",
            "guarded-expense@example.com",
            "secret1"
        ).getOrThrow()
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val expense = ExpenseFormViewModel(
            environment.auth,
            environment.expenses,
            SavedStateHandle()
        )
        try {
            expense.onAmountChange("12.50")
            expense.onDescriptionChange("Vet check")
            expense.submit()
            assertTrue(expense.state.value.isSubmitting)
            expense.submit()
            assertTrue(expense.state.value.isSubmitting)

            awaitCondition(description = "guarded expense save") {
                expense.state.value.success
            }
            expense.submit()
            assertTrue(expense.state.value.success)
            assertEquals(
                1,
                environment.database.expenseDao().observeForOwner(owner.id).first().size
            )
        } finally {
            expense.viewModelScope.cancel()
            Dispatchers.resetMain()
        }

        val taskOwner = environment.auth.register(
            "Guarded Task Owner",
            "guarded-task@example.com",
            "secret1"
        ).getOrThrow()
        val petId = environment.pets.save(
            Pet(ownerId = taskOwner.id, name = "Milo", species = Species.DOG.name)
        )
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val task = TaskFormViewModel(
            environment.auth,
            environment.pets,
            environment.tasks,
            environment.imageStorage,
            SavedStateHandle()
        )
        try {
            awaitCondition(description = "guarded task form load") {
                !task.state.value.isLoading
            }
            assertEquals(petId, task.state.value.pet?.id)
            task.onNameChange("Morning walk")
            task.onTimeChange(java.time.LocalTime.of(8, 0))

            task.save()
            assertTrue(task.state.value.isSaving)
            task.save()
            assertTrue(task.state.value.isSaving)

            awaitCondition(description = "guarded task save") {
                task.state.value.saved
            }
            task.save()
            assertTrue(task.state.value.saved)
            assertEquals(1, environment.database.careTaskDao().listForPet(petId, taskOwner.id).size)
        } finally {
            task.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `login can retry after a repository failure`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val user = environment.auth.register(
            "Retry Login",
            "retry-login@example.com",
            "secret1"
        ).getOrThrow()
        environment.auth.logout()
        val viewModel = LoginViewModel(environment.auth)
        try {
            viewModel.onEmailChange(user.email)
            viewModel.onPasswordChange("wrong-password")
            viewModel.submit()
            awaitCondition(description = "login failure") {
                viewModel.state.value.formError != null
            }
            assertFalse(viewModel.state.value.isSubmitting)

            viewModel.onPasswordChange("secret1")
            viewModel.submit()
            awaitCondition(description = "login retry") {
                viewModel.state.value.success
            }
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `registration can retry after a duplicate-account failure`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        environment.auth.register("Existing User", "retry-register@example.com", "secret1")
        environment.auth.logout()
        val viewModel = RegisterViewModel(environment.auth)
        try {
            viewModel.onNameChange("Retry User")
            viewModel.onEmailChange("retry-register@example.com")
            viewModel.onPasswordChange("secret1")
            viewModel.onConfirmChange("secret1")
            viewModel.submit()
            awaitCondition(description = "registration failure") {
                viewModel.state.value.formError != null
            }
            assertFalse(viewModel.state.value.isSubmitting)

            viewModel.onEmailChange("retry-register-new@example.com")
            viewModel.submit()
            awaitCondition(description = "registration retry") {
                viewModel.state.value.success
            }
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `expense can retry after a signed-out repository failure`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = ExpenseFormViewModel(
            environment.auth,
            environment.expenses,
            SavedStateHandle()
        )
        try {
            viewModel.onAmountChange("8.25")
            viewModel.onDescriptionChange("Retry expense")
            viewModel.submit()
            awaitCondition(description = "expense failure") {
                viewModel.state.value.formError != null
            }
            assertEquals("Not signed in", viewModel.state.value.formError)
            assertFalse(viewModel.state.value.isSubmitting)

            val owner = environment.auth.register(
                "Retry Expense Owner",
                "retry-expense@example.com",
                "secret1"
            ).getOrThrow()
            viewModel.submit()
            awaitCondition(description = "expense retry") {
                viewModel.state.value.success
            }
            assertEquals(
                1,
                environment.database.expenseDao().observeForOwner(owner.id).first().size
            )
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `task can retry after a signed-out repository failure`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = environment.auth.register(
            "Retry Task Owner",
            "retry-task@example.com",
            "secret1"
        ).getOrThrow()
        val petId = environment.pets.save(
            Pet(ownerId = owner.id, name = "Luna", species = Species.CAT.name)
        )
        val viewModel = TaskFormViewModel(
            environment.auth,
            environment.pets,
            environment.tasks,
            environment.imageStorage,
            SavedStateHandle()
        )
        try {
            awaitCondition(description = "retry task form load") {
                !viewModel.state.value.isLoading
            }
            assertEquals(petId, viewModel.state.value.pet?.id)
            viewModel.onNameChange("Medication")
            viewModel.onTimeChange(java.time.LocalTime.of(9, 0))

            environment.auth.logout()
            viewModel.save()
            awaitCondition(description = "task failure") {
                viewModel.state.value.formError != null
            }
            assertFalse(viewModel.state.value.isSaving)

            environment.auth.login(owner.email, "secret1").getOrThrow()
            viewModel.save()
            awaitCondition(description = "task retry") {
                viewModel.state.value.saved
            }
            assertEquals(1, environment.database.careTaskDao().listForPet(petId, owner.id).size)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }
}