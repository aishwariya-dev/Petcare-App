package com.petcare.app.data

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.petcare.app.data.preferences.UserPreferences
import com.petcare.app.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class UserPreferencesTest {
    private lateinit var environment: TestEnvironment

    @Before
    fun setUp() {
        environment = TestEnvironment(ApplicationProvider.getApplicationContext<Context>())
        runBlocking {
            environment.preferences.setUserId(null)
            environment.preferences.setThemeMode(ThemeMode.SYSTEM)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            environment.preferences.setUserId(null)
            environment.preferences.setThemeMode(ThemeMode.SYSTEM)
        }
        environment.close()
    }

    @Test
    fun `session preference persists and can be cleared`() = runBlocking {
        assertNull(environment.preferences.currentUserId())
        environment.preferences.setUserId(42L)
        assertEquals(42L, environment.preferences.currentUserId())

        val reopened = UserPreferences(environment.preferencesContextForTest)
        assertEquals(42L, reopened.userId.first())

        environment.preferences.setUserId(null)
        assertNull(reopened.currentUserId())
    }

    @Test
    fun `theme preference persists across preference instances`() = runBlocking {
        assertEquals(ThemeMode.SYSTEM, environment.preferences.themeMode.first())
        environment.preferences.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, environment.preferences.themeMode.first())

        val reopened = UserPreferences(environment.preferencesContextForTest)
        assertEquals(ThemeMode.DARK, reopened.themeMode.first())
        reopened.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, environment.preferences.themeMode.first())
    }

    @Test
    fun `permissions intro flag is device wide and defaults to unseen`() = runBlocking {
        assertFalse(environment.preferences.permissionsIntroSeen.first())
        environment.preferences.setPermissionsIntroSeen()

        val reopened = UserPreferences(environment.preferencesContextForTest)
        assertTrue(reopened.permissionsIntroSeen.first())
    }

    @Test
    fun `reminders master switch defaults on and is isolated per local account`() = runBlocking {
        assertTrue(environment.preferences.remindersEnabled(1L).first())
        environment.preferences.setRemindersEnabled(1L, false)

        val reopened = UserPreferences(environment.preferencesContextForTest)
        assertFalse(reopened.remindersEnabled(1L).first())
        assertTrue(reopened.remindersEnabled(2L).first())
        assertTrue(legacyDataStore(environment.preferencesContextForTest).data.first()
            .contains(booleanPreferencesKey("reminders_enabled_1")))
    }

    @Test
    fun `sms consent defaults off and is isolated per local account`() = runBlocking {
        assertFalse(environment.preferences.smsConsent(1L).first())
        environment.preferences.setSmsConsent(1L)

        val reopened = UserPreferences(environment.preferencesContextForTest)
        assertTrue(reopened.smsConsent(1L).first())
        assertFalse(reopened.smsConsent(2L).first())
        assertTrue(legacyDataStore(environment.preferencesContextForTest).data.first()
            .contains(booleanPreferencesKey("sms_consent_1")))
    }

    @Suppress("UNCHECKED_CAST")
    private fun legacyDataStore(context: Context): androidx.datastore.core.DataStore<Preferences> {
        val method = Class.forName("com.petcare.app.data.preferences.UserPreferencesKt")
            .getDeclaredMethod("getDataStore", Context::class.java)
        method.isAccessible = true
        return method.invoke(null, context) as androidx.datastore.core.DataStore<Preferences>
    }

    @Test
    fun `remembered SMS recipients are per account most recent first deduplicated and capped`() = runBlocking {
        val prefs = environment.preferences
        assertEquals(emptyList<String>(), prefs.smsRecipients(7L).first())

        prefs.rememberSmsRecipient(7L, " +44 7700 900001 ")
        prefs.rememberSmsRecipient(7L, "+44 7700 900002")
        prefs.rememberSmsRecipient(7L, "+44 7700 900001")
        assertEquals(listOf("+44 7700 900001", "+44 7700 900002"), prefs.smsRecipients(7L).first())
        assertEquals(emptyList<String>(), prefs.smsRecipients(8L).first())

        prefs.rememberSmsRecipient(7L, "   ")
        assertEquals(2, prefs.smsRecipients(7L).first().size)

        (3..9).forEach { prefs.rememberSmsRecipient(7L, "+44 7700 90000$it") }
        val capped = prefs.smsRecipients(7L).first()
        assertEquals(UserPreferences.MAX_REMEMBERED_SMS_RECIPIENTS, capped.size)
        assertEquals("+44 7700 900009", capped.first())

        prefs.forgetSmsRecipient(7L, "+44 7700 900009")
        assertFalse(prefs.smsRecipients(7L).first().contains("+44 7700 900009"))
        capped.drop(1).forEach { prefs.forgetSmsRecipient(7L, it) }
        assertEquals(emptyList<String>(), prefs.smsRecipients(7L).first())
    }
}
