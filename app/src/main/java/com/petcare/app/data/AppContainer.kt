package com.petcare.app.data

import android.content.Context
import com.petcare.app.data.local.DatabaseSeeder
import com.petcare.app.data.local.PetCareDatabase
import com.petcare.app.data.preferences.UserPreferences
import com.petcare.app.data.repository.AuthRepository
import com.petcare.app.data.repository.ExpenseRepository
import com.petcare.app.data.repository.LocationRepository
import com.petcare.app.data.repository.PetRepository
import com.petcare.app.data.repository.TaskRepository
import com.petcare.app.data.repository.SmsReminderRepository
import com.petcare.app.permissions.AndroidDevicePermissions
import com.petcare.app.permissions.DevicePermissions
import com.petcare.app.util.ImageStorage

/**
 * Simple service locator (no DI framework).
 * Data flow: UI → ViewModel → Repository → Room database.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val database: PetCareDatabase by lazy { PetCareDatabase.getInstance(appContext) }

    val preferences: UserPreferences by lazy { UserPreferences(appContext) }
    val imageStorage: ImageStorage by lazy { ImageStorage(appContext) }
    val devicePermissions: DevicePermissions by lazy { AndroidDevicePermissions(appContext) }

    /** Inserts the demo household on first launch. Safe to call every start. */
    suspend fun ensureSeeded() = DatabaseSeeder.seed(database)

    val authRepository: AuthRepository by lazy {
        AuthRepository(database.userDao(), preferences)
    }

    val petRepository: PetRepository by lazy {
        PetRepository(database.petDao(), database.careTaskDao(), imageStorage, authRepository)
    }

    val taskRepository: TaskRepository by lazy {
        TaskRepository(database.careTaskDao(), imageStorage, authRepository)
    }

    val smsReminderRepository: SmsReminderRepository by lazy {
        SmsReminderRepository(database.careTaskDao(), database.smsReminderOccurrenceDao(), database.petDao())
    }

    val expenseRepository: ExpenseRepository by lazy {
        ExpenseRepository(database.expenseDao(), authRepository)
    }

    /** Legacy saved-location data remains owner-scoped even though no map UI is exposed. */
    val locationRepository: LocationRepository by lazy {
        LocationRepository(database.petLocationDao(), authRepository)
    }

}
