package com.petcare.app.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.petcare.app.data.TestEnvironment
import com.petcare.app.data.awaitCondition
import com.petcare.app.ui.screens.auth.LoginViewModel
import com.petcare.app.ui.screens.auth.RegisterViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AuthViewModelTest {
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
    fun `login ViewModel reports field errors and completes a valid session`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val user = environment.auth.register("Login User", "login-vm@example.com", "secret1")
            .getOrThrow()
        environment.auth.logout()
        val viewModel = LoginViewModel(environment.auth)
        try {
            viewModel.submit()
            assertEquals("Email is required", viewModel.state.value.emailError)
            assertEquals("Password is required", viewModel.state.value.passwordError)
            assertFalse(viewModel.state.value.isSubmitting)

            viewModel.onEmailChange(user.email)
            viewModel.onPasswordChange("secret1")
            viewModel.submit()
            awaitCondition(description = "successful login state") {
                viewModel.state.value.success
            }
            assertEquals(user.id, environment.auth.signedInUserId())
            assertEquals(user.name, viewModel.state.value.signedInName)
            assertFalse(viewModel.state.value.isSubmitting)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `login ViewModel exposes repository failure for a wrong password`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        environment.auth.register("Login User", "wrong-password@example.com", "secret1")
        environment.auth.logout()
        val viewModel = LoginViewModel(environment.auth)
        try {
            viewModel.onEmailChange("wrong-password@example.com")
            viewModel.onPasswordChange("incorrect")
            viewModel.submit()
            awaitCondition(description = "login form error") {
                viewModel.state.value.formError != null
            }
            assertEquals(
                "No account matches that email and password.",
                viewModel.state.value.formError
            )
            assertFalse(viewModel.state.value.success)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `register ViewModel validates fields then saves all form values`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = RegisterViewModel(environment.auth)
        try {
            viewModel.submit()
            assertEquals("Name is required", viewModel.state.value.nameError)
            assertEquals("Email is required", viewModel.state.value.emailError)
            assertEquals("Password is required", viewModel.state.value.passwordError)
            assertEquals("Please confirm your password", viewModel.state.value.confirmError)

            viewModel.onNameChange(" New User ")
            viewModel.onEmailChange("NEW-USER@example.com")
            viewModel.onPasswordChange("secret1")
            viewModel.onConfirmChange("secret1")
            viewModel.onPhoneChange("+1 555 0199")
            viewModel.submit()
            awaitCondition(description = "successful registration state") {
                viewModel.state.value.success
            }

            val saved = environment.database.userDao().findByEmail("new-user@example.com")
            assertTrue(saved != null)
            assertEquals("New User", saved!!.name)
            assertEquals("+1 555 0199", saved.phoneNumber)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }
}