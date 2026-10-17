package com.petcare.app

import android.app.Application
import com.petcare.app.data.AppContainer
import com.petcare.app.reminder.ReminderAlarmScheduler
import com.petcare.app.reminder.ReminderCoordinator
import com.petcare.app.reminder.AndroidAutomaticSmsSender

/**
 * Application entry point. Owns the single [AppContainer] that wires the
 * database, preferences and repositories together (manual dependency injection).
 */
class PetCareApp : Application() {

    lateinit var container: AppContainer
        private set

    lateinit var reminderCoordinator: ReminderCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        reminderCoordinator = ReminderCoordinator(
            authRepository = container.authRepository,
            taskRepository = container.taskRepository,
            scheduler = ReminderAlarmScheduler(this),
            preferences = container.preferences,
            smsRepository = container.smsReminderRepository,
            smsSender = AndroidAutomaticSmsSender(this)
        )
        reminderCoordinator.start()
    }
}
