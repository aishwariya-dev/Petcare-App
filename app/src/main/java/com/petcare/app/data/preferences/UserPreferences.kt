package com.petcare.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.petcare.app.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "petcare_prefs")

/**
 * Small, non-relational preferences: the signed-in account id, the theme, and
 * the few flags below. Account-scoped flags embed the account id in the key so
 * two local accounts on one phone never share them.
 */
class UserPreferences(private val context: Context) {

    private object Keys {
        val USER_ID = longPreferencesKey("user_id")
        val THEME_MODE = stringPreferencesKey("theme_mode")

        /** Device-level: the permissions page was shown once on this phone. */
        val PERMISSIONS_INTRO_SEEN = booleanPreferencesKey("permissions_intro_seen")

        fun remindersEnabled(userId: Long) = booleanPreferencesKey("reminders_enabled_$userId")
        fun smsConsent(userId: Long) = booleanPreferencesKey("sms_consent_$userId")
        fun smsRecipients(userId: Long) = stringPreferencesKey("sms_recipients_$userId")
    }

    companion object {
        /** Most recent first; enough for a household's few carers without becoming a contact list. */
        const val MAX_REMEMBERED_SMS_RECIPIENTS = 5
        private const val RECIPIENT_SEPARATOR = "\n"
    }

    val userId: Flow<Long?> = context.dataStore.data.map { it[Keys.USER_ID] }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { ThemeMode.from(it[Keys.THEME_MODE]) }

    val permissionsIntroSeen: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.PERMISSIONS_INTRO_SEEN] ?: false }

    /** Master switch for notifications and automatic SMS; on until the account turns it off. */
    fun remindersEnabled(userId: Long): Flow<Boolean> =
        context.dataStore.data.map { it[Keys.remindersEnabled(userId)] ?: true }

    /** One-time agreement to the SMS terms, given once per account. */
    fun smsConsent(userId: Long): Flow<Boolean> =
        context.dataStore.data.map { it[Keys.smsConsent(userId)] ?: false }

    /**
     * Phone numbers this account has already sent automatic SMS to, most recent
     * first, so a new task can be pre-filled instead of asking every time.
     */
    fun smsRecipients(userId: Long): Flow<List<String>> =
        context.dataStore.data.map { prefs ->
            prefs[Keys.smsRecipients(userId)].orEmpty()
                .split(RECIPIENT_SEPARATOR)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
        }

    suspend fun rememberSmsRecipient(userId: Long, recipient: String) {
        val number = recipient.trim()
        if (number.isEmpty()) return
        context.dataStore.edit { prefs ->
            val existing = prefs[Keys.smsRecipients(userId)].orEmpty()
                .split(RECIPIENT_SEPARATOR)
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != number }
            prefs[Keys.smsRecipients(userId)] =
                (listOf(number) + existing).take(MAX_REMEMBERED_SMS_RECIPIENTS).joinToString(RECIPIENT_SEPARATOR)
        }
    }

    suspend fun forgetSmsRecipient(userId: Long, recipient: String) {
        val number = recipient.trim()
        context.dataStore.edit { prefs ->
            val remaining = prefs[Keys.smsRecipients(userId)].orEmpty()
                .split(RECIPIENT_SEPARATOR)
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != number }
            if (remaining.isEmpty()) prefs.remove(Keys.smsRecipients(userId))
            else prefs[Keys.smsRecipients(userId)] = remaining.joinToString(RECIPIENT_SEPARATOR)
        }
    }

    suspend fun currentUserId(): Long? = userId.first()

    suspend fun setUserId(id: Long?) {
        context.dataStore.edit {
            if (id == null) it.remove(Keys.USER_ID) else it[Keys.USER_ID] = id
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setPermissionsIntroSeen() {
        context.dataStore.edit { it[Keys.PERMISSIONS_INTRO_SEEN] = true }
    }

    suspend fun setRemindersEnabled(userId: Long, enabled: Boolean) {
        context.dataStore.edit { it[Keys.remindersEnabled(userId)] = enabled }
    }

    suspend fun setSmsConsent(userId: Long) {
        context.dataStore.edit { it[Keys.smsConsent(userId)] = true }
    }
}
