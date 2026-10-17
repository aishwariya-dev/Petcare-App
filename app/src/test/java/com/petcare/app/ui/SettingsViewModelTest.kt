package com.petcare.app.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.petcare.app.data.TestEnvironment
import com.petcare.app.data.awaitCondition
import com.petcare.app.model.ThemeMode
import com.petcare.app.ui.screens.settings.ProfileViewModel
import com.petcare.app.ui.screens.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SettingsViewModelTest {
    private lateinit var environment: TestEnvironment

    @Before
    fun setUp() {
        environment = TestEnvironment(ApplicationProvider.getApplicationContext<Context>())
        runBlocking {
            environment.auth.logout()
            environment.preferences.setThemeMode(ThemeMode.SYSTEM)
        }
    }

    @After
    fun tearDown() {
        runBlocking { environment.auth.logout() }
        environment.close()
    }

    @Test
    fun `settings ViewModel persists selected theme and logout callback`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val user = environment.auth.register("Settings User", "settings@example.com", "secret1")
            .getOrThrow()
        val viewModel = SettingsViewModel(environment.auth, environment.preferences)
        var loggedOut = false
        var reopened: SettingsViewModel? = null
        val settingsCollector = launch {
            viewModel.user.collect { }
        }
        val themeCollector = launch {
            viewModel.themeMode.collect { }
        }
        var reopenedCollector: kotlinx.coroutines.Job? = null
        var reopenedUserCollector: kotlinx.coroutines.Job? = null
        try {
            awaitCondition(description = "settings user") {
                viewModel.user.value?.id == user.id
            }
            assertEquals(ThemeMode.SYSTEM, viewModel.themeMode.value)

            viewModel.setThemeMode(ThemeMode.DARK)
            awaitCondition(description = "dark theme preference") {
                viewModel.themeMode.value == ThemeMode.DARK
            }
            assertEquals(ThemeMode.DARK, environment.preferences.themeMode.first())

            val newViewModel = SettingsViewModel(environment.auth, environment.preferences)
            reopened = newViewModel
            reopenedCollector = launch {
                newViewModel.themeMode.collect { }
            }
            reopenedUserCollector = launch {
                newViewModel.user.collect { }
            }
            awaitCondition(description = "reopened dark theme preference") {
                newViewModel.themeMode.value == ThemeMode.DARK
            }

            viewModel.logout { loggedOut = true }
            awaitCondition(description = "logout callback") { loggedOut }
            assertNull(environment.auth.signedInUserId())
            assertNull(newViewModel.user.value)
            assertEquals(ThemeMode.DARK, environment.preferences.themeMode.first())
            assertTrue(loggedOut)
        } finally {
            viewModel.viewModelScope.cancel()
            reopened?.viewModelScope?.cancel()
            settingsCollector.cancel()
            themeCollector.cancel()
            reopenedCollector?.cancel()
            reopenedUserCollector?.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `profile ViewModel validates phone and saves edited account fields`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val user = environment.auth.register(
            "Profile User",
            "profile-settings@example.com",
            "secret1"
        ).getOrThrow()
        val viewModel = ProfileViewModel(environment.auth, environment.imageStorage)
        try {
            awaitCondition(description = "profile state load") {
                viewModel.state.value.loaded && viewModel.state.value.user?.id == user.id
            }

            viewModel.onPhoneChange("not a phone")
            viewModel.save(onSaved = {}, onError = {})
            assertEquals("Enter a valid phone number", viewModel.state.value.phoneError)
            assertEquals("Profile User", environment.database.userDao().findById(user.id)!!.name)

            var saved = false
            viewModel.onNameChange("Updated Profile")
            viewModel.onPhoneChange("+1 555 0101")
            viewModel.save(onSaved = { saved = true }, onError = {})
            awaitCondition(description = "profile save callback") { saved }

            val updated = environment.database.userDao().findById(user.id)!!
            assertEquals("Updated Profile", updated.name)
            assertEquals("+1 555 0101", updated.phoneNumber)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }
}