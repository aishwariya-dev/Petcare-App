package com.petcare.app.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.petcare.app.data.TestEnvironment
import com.petcare.app.data.awaitCondition
import com.petcare.app.model.CareTask
import com.petcare.app.model.Frequency
import com.petcare.app.model.Pet
import com.petcare.app.model.Species
import com.petcare.app.model.TaskCategory
import com.petcare.app.navigation.Routes
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
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TaskFormViewModelTest {
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
    fun `new task form validates reminder time and persists schedule fields`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = environment.auth.register("Task Owner", "task-form@example.com", "secret1").getOrThrow()
        val petId = environment.pets.save(
            Pet(ownerId = owner.id, name = "Milo", species = Species.DOG.name)
        )
        val viewModel = TaskFormViewModel(
            environment.auth,
            environment.pets,
            environment.tasks,
            environment.imageStorage,
            SavedStateHandle()
        )
        try {
            awaitCondition(description = "new task form load") {
                !viewModel.state.value.isLoading
            }
            assertFalse(viewModel.state.value.isEdit)
            assertEquals(petId, viewModel.state.value.pet!!.id)

            viewModel.onReminderEnabledChange(true)
            viewModel.save()
            assertEquals("Task name is required", viewModel.state.value.nameError)
            assertEquals("Choose a time for reminders", viewModel.state.value.timeError)
            assertFalse(viewModel.state.value.isSaving)

            viewModel.onNameChange(" Morning walk ")
            viewModel.onCategoryChange(TaskCategory.EXERCISE)
            viewModel.onFrequencyChange(Frequency.WEEKLY)
            viewModel.onDateChange(LocalDate.of(2025, 5, 12))
            viewModel.onTimeChange(LocalTime.of(8, 30))
            viewModel.onSmsRecipientChange("+44 7700 900000")
            viewModel.onSmsReminderEnabledChange(true)
            viewModel.onSuppliesChange("Lead, water")
            viewModel.onNotesChange("Short route")
            viewModel.save()
            awaitCondition(description = "new task save") { viewModel.state.value.saved }
            val preview = viewModel.state.value.reminderPreview!!
            assertEquals("8:30 AM", preview.notification.time)
            assertEquals("Care reminder", preview.notification.title)
            assertEquals("+44 7700 900000", preview.sms!!.recipient)
            assertTrue(preview.sms.message.startsWith("PetCare: Milo (Dog)\n"))
            assertTrue(preview.sms.message.contains("Morning walk\n"))
            assertTrue(preview.sms.message.contains("Note: Short route\n"))

            val saved = environment.tasks.getTask(
                environment.database.careTaskDao().listForPet(petId, owner.id).single().id
            )!!
            assertEquals("Morning walk", saved.name)
            assertEquals(TaskCategory.EXERCISE.name, saved.category)
            assertEquals(Frequency.WEEKLY.name, saved.frequency)
            assertEquals(LocalDate.of(2025, 5, 12).toEpochDay(), saved.startDateEpochDay)
            assertEquals(8 * 60 + 30, saved.timeMinutes)
            assertTrue(saved.reminderEnabled)
            assertTrue(saved.smsReminderEnabled)
            assertEquals("Lead, water", saved.supplies)
            assertEquals("Short route", saved.notes)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `edit task hydrates reminders and clears completion when schedule changes`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = environment.auth.register("Edit Task", "task-edit@example.com", "secret1").getOrThrow()
        val petId = environment.pets.save(
            Pet(ownerId = owner.id, name = "Luna", species = Species.CAT.name)
        )
        val today = LocalDate.now()
        val taskId = environment.tasks.save(
            CareTask(
                petId = petId,
                name = "Medication",
                category = TaskCategory.MEDICATION.name,
                frequency = Frequency.DAILY.name,
                startDateEpochDay = today.toEpochDay(),
                timeMinutes = 9 * 60,
                reminderEnabled = true,
                smsReminderEnabled = true,
                smsRecipient = "+44 7700 900000",
                lastCompletedEpochDay = today.toEpochDay()
            )
        )
        val viewModel = TaskFormViewModel(
            environment.auth,
            environment.pets,
            environment.tasks,
            environment.imageStorage,
            SavedStateHandle(mapOf(Routes.ARG_TASK_ID to taskId))
        )
        try {
            awaitCondition(description = "task edit hydration") {
                !viewModel.state.value.isLoading
            }
            assertTrue(viewModel.state.value.isEdit)
            assertEquals("Medication", viewModel.state.value.name)
            assertEquals(TaskCategory.MEDICATION, viewModel.state.value.category)
            assertEquals(Frequency.DAILY, viewModel.state.value.frequency)
            assertTrue(viewModel.state.value.reminderEnabled)
            assertTrue(viewModel.state.value.smsReminderEnabled)
            assertEquals("+44 7700 900000", viewModel.state.value.smsRecipient)
            assertEquals(LocalTime.of(9, 0), viewModel.state.value.time)

            viewModel.onNameChange("Medication updated")
            // SMS consent is per account, so editing the number keeps the switch as it is.
            viewModel.onSmsRecipientChange("+44 7700 900001")
            assertTrue(viewModel.state.value.smsReminderEnabled)
            viewModel.onDateChange(today.plusDays(1))
            viewModel.onReminderEnabledChange(false)
            viewModel.onSmsReminderEnabledChange(false)
            viewModel.save()
            awaitCondition(description = "edited task save") { viewModel.state.value.saved }

            val saved = environment.tasks.getTask(taskId)!!
            assertEquals("Medication updated", saved.name)
            assertFalse(saved.reminderEnabled)
            assertFalse(saved.smsReminderEnabled)
            assertEquals("+44 7700 900001", saved.smsRecipient)
            assertEquals(today.plusDays(1).toEpochDay(), saved.startDateEpochDay)
            assertEquals(null, saved.lastCompletedEpochDay)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `editing a missing task reports a load error`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        environment.auth.register("Missing Task", "missing-task@example.com", "secret1")
        val viewModel = TaskFormViewModel(
            environment.auth,
            environment.pets,
            environment.tasks,
            environment.imageStorage,
            SavedStateHandle(mapOf(Routes.ARG_TASK_ID to 9999L))
        )
        try {
            awaitCondition(description = "missing task load") {
                !viewModel.state.value.isLoading
            }
            assertEquals("This task no longer exists.", viewModel.state.value.loadError)
            assertNotNull(viewModel.state.value.loadError)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `new task pre-fills the last SMS recipient and an Upcoming start day`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = environment.auth.register("Prefill Owner", "prefill-${System.nanoTime()}@example.com", "secret1").getOrThrow()
        environment.pets.save(Pet(ownerId = owner.id, name = "Milo", species = Species.DOG.name))
        environment.preferences.setSmsConsent(owner.id)
        environment.preferences.rememberSmsRecipient(owner.id, "+44 7700 900111")
        environment.preferences.rememberSmsRecipient(owner.id, "+44 7700 900222")
        val tomorrow = LocalDate.now().plusDays(1)

        val viewModel = TaskFormViewModel(
            environment.auth, environment.pets, environment.tasks, environment.imageStorage,
            SavedStateHandle(mapOf(Routes.ARG_START_EPOCH_DAY to tomorrow.toEpochDay())),
            preferences = environment.preferences
        )
        try {
            awaitCondition(description = "prefilled form load") { !viewModel.state.value.isLoading }
            assertEquals(tomorrow, viewModel.state.value.date)
            assertEquals("+44 7700 900222", viewModel.state.value.smsRecipient)
            assertEquals(listOf("+44 7700 900222", "+44 7700 900111"), viewModel.state.value.savedSmsRecipients)
            assertFalse(viewModel.state.value.smsReminderEnabled)

            viewModel.forgetSmsRecipient("+44 7700 900111")
            awaitCondition(description = "forget recipient") { viewModel.state.value.savedSmsRecipients.size == 1 }

            viewModel.onNameChange("Evening walk")
            viewModel.onTimeChange(LocalTime.of(18, 0))
            viewModel.onSmsReminderEnabledChange(true)
            viewModel.onSmsRecipientChange("+44 7700 900333")
            viewModel.save()
            awaitCondition(description = "prefilled form save") { viewModel.state.value.saved }
            assertEquals(
                listOf("+44 7700 900333", "+44 7700 900222"),
                environment.preferences.smsRecipients(owner.id).first()
            )
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }
}
