package com.petcare.app.ui.screens.tasks

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petcare.app.data.repository.AuthRepository
import com.petcare.app.data.repository.PetRepository
import com.petcare.app.data.repository.TaskRepository
import com.petcare.app.model.CareTask
import com.petcare.app.model.Frequency
import com.petcare.app.model.Pet
import com.petcare.app.model.TaskCategory
import com.petcare.app.navigation.Routes
import com.petcare.app.domain.TaskSchedule
import com.petcare.app.reminder.ReminderNotification
import com.petcare.app.reminder.TaskReminderPreview
import com.petcare.app.ui.common.userMessage
import com.petcare.app.util.ImageStorage
import com.petcare.app.util.Validators
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import com.petcare.app.data.preferences.UserPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

data class TaskFormState(
    val isEdit: Boolean = false,
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val pets: List<Pet> = emptyList(),
    val pet: Pet? = null,
    val name: String = "",
    val category: TaskCategory = TaskCategory.FEEDING,
    val frequency: Frequency = Frequency.DAILY,
    val date: LocalDate? = LocalDate.now(),
    val time: LocalTime? = null,
    val reminderEnabled: Boolean = false,
    val smsReminderEnabled: Boolean = false,
    val smsRecipient: String = "",
    val supplies: String = "",
    val notes: String = "",
    val existingImagePath: String? = null,
    val pendingImageUri: Uri? = null,
    val imageRemoved: Boolean = false,
    val petError: String? = null,
    val nameError: String? = null,
    val dateError: String? = null,
    val timeError: String? = null,
    val smsRecipientError: String? = null,
    /** Numbers this account already used for automatic SMS, most recent first. */
    val savedSmsRecipients: List<String> = emptyList(),
    val formError: String? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val reminderPreview: TaskReminderPreview? = null
) {
    val visibleImagePath: String? get() = if (imageRemoved) null else existingImagePath
    val dateLabel: String get() = if (frequency == Frequency.ONCE) "Date" else "Starts on"
}

/** Screens 12 (create) and 14 (edit). */
class TaskFormViewModel(
    private val authRepository: AuthRepository,
    private val petRepository: PetRepository,
    private val taskRepository: TaskRepository,
    private val imageStorage: ImageStorage,
    savedStateHandle: SavedStateHandle,
    /** Account-scoped SMS consent; null (tests) behaves as "not yet agreed". */
    private val preferences: UserPreferences? = null
) : ViewModel() {

    /** True once this account has accepted the SMS terms on any screen. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val smsConsent: StateFlow<Boolean> = authRepository.currentUser
        .flatMapLatest { user ->
            if (user == null || preferences == null) flowOf(false) else preferences.smsConsent(user.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var consentWrite: Job? = null

    fun grantSmsConsent() {
        val store = preferences ?: return
        consentWrite = viewModelScope.launch {
            val userId = authRepository.signedInUserId() ?: return@launch
            authRepository.smsAuthorizationGate.withLock { store.setSmsConsent(userId) }
        }
    }

    /**
     * Automatic SMS is saved only once this account's consent is durably
     * stored; a consent write still in flight is awaited, never assumed.
     */
    private suspend fun requireSmsConsent() {
        val store = preferences ?: return
        consentWrite?.join()
        val userId = authRepository.signedInUserId()
        check(userId != null && store.smsConsent(userId).first()) {
            "Agree to the automatic SMS terms before turning it on."
        }
    }

    private val taskId: Long = savedStateHandle.get<Long>(Routes.ARG_TASK_ID) ?: -1L
    private val presetPetId: Long = savedStateHandle.get<Long>(Routes.ARG_PET_ID) ?: -1L
    private val presetStartEpochDay: Long = savedStateHandle.get<Long>(Routes.ARG_START_EPOCH_DAY) ?: -1L
    private var existing: CareTask? = null

    private val _state = MutableStateFlow(TaskFormState(isEdit = taskId > 0))
    val state: StateFlow<TaskFormState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val user = authRepository.currentUser.first()
                    ?: throw IllegalStateException("You need to be logged in.")
                val pets = petRepository.observePets(user.id).first()

                if (taskId > 0) {
                    val task = taskRepository.getTask(taskId)
                        ?: run {
                            _state.update { it.copy(isLoading = false, loadError = "This task no longer exists.") }
                            return@launch
                        }
                    val startDate = runCatching { LocalDate.ofEpochDay(task.startDateEpochDay) }.getOrNull()
                    val time = runCatching { LocalTime.ofSecondOfDay(task.timeMinutes * 60L) }.getOrNull()
                    if (startDate == null || time == null) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                pets = pets,
                                loadError = "This task has invalid saved schedule data and cannot be edited."
                            )
                        }
                        return@launch
                    }
                    existing = task
                    _state.update {
                        it.copy(
                            isLoading = false,
                            pets = pets,
                            pet = pets.firstOrNull { p -> p.id == task.petId },
                            name = task.name,
                            category = TaskCategory.from(task.category),
                            frequency = Frequency.from(task.frequency),
                            date = startDate,
                            time = time,
                            reminderEnabled = task.reminderEnabled,
                            smsReminderEnabled = task.smsReminderEnabled,
                            smsRecipient = task.smsRecipient,
                            supplies = task.supplies,
                            notes = task.notes,
                            existingImagePath = task.imagePath,
                            savedSmsRecipients = preferences?.smsRecipients(user.id)?.first().orEmpty()
                        )
                    }
                } else {
                    val preset = pets.firstOrNull { it.id == presetPetId } ?: pets.singleOrNull()
                    val presetDate = presetStartEpochDay.takeIf { it >= 0L }
                        ?.let { day -> runCatching { LocalDate.ofEpochDay(day) }.getOrNull() }
                    val remembered = preferences?.smsRecipients(user.id)?.first().orEmpty()
                    _state.update {
                        it.copy(
                            isLoading = false, pets = pets, pet = preset,
                            date = presetDate ?: it.date,
                            // Pre-fill the last recipient; the number stays editable and
                            // SMS itself stays off until the user turns it on.
                            smsRecipient = remembered.firstOrNull().orEmpty(),
                            savedSmsRecipients = remembered
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, loadError = e.userMessage()) }
            }
        }
    }

    /** Called when the screen resumes so a pet added mid-flow appears in the dropdown. */
    fun refreshPets() {
        viewModelScope.launch {
            val user = authRepository.currentUser.first() ?: return@launch
            val pets = petRepository.observePets(user.id).first()
            _state.update { s -> s.copy(pets = pets, pet = s.pet ?: pets.singleOrNull()) }
        }
    }

    fun onPetChange(v: Pet) = _state.update { it.copy(pet = v, petError = null) }
    fun onNameChange(v: String) = _state.update { it.copy(name = v, nameError = null) }
    fun onCategoryChange(v: TaskCategory) = _state.update { it.copy(category = v) }
    fun onFrequencyChange(v: Frequency) = _state.update { it.copy(frequency = v, dateError = null) }
    fun onDateChange(v: LocalDate) = _state.update { it.copy(date = v, dateError = null) }
    fun onTimeChange(v: LocalTime) = _state.update { it.copy(time = v, timeError = null) }
    fun onReminderEnabledChange(v: Boolean) = _state.update { it.copy(reminderEnabled = v) }
    fun onSmsReminderEnabledChange(v: Boolean) = _state.update { it.copy(smsReminderEnabled = v) }
    fun onSmsRecipientChange(v: String) = _state.update { it.copy(smsRecipient = v, smsRecipientError = null) }

    fun forgetSmsRecipient(number: String) {
        val store = preferences ?: return
        viewModelScope.launch {
            val userId = authRepository.signedInUserId() ?: return@launch
            store.forgetSmsRecipient(userId, number)
            val remaining = store.smsRecipients(userId).first()
            _state.update { it.copy(savedSmsRecipients = remaining) }
        }
    }
    fun onSuppliesChange(v: String) = _state.update { it.copy(supplies = v) }
    fun onNotesChange(v: String) = _state.update { it.copy(notes = v) }
    fun onImagePicked(uri: Uri) {
        imageStorage.discardCapture(_state.value.pendingImageUri?.takeIf { it != uri })
        _state.update { it.copy(pendingImageUri = uri, imageRemoved = false) }
    }

    fun onImageRemoved() {
        imageStorage.discardCapture(_state.value.pendingImageUri)
        _state.update { it.copy(pendingImageUri = null, imageRemoved = true) }
    }

    fun save() {
        if (_state.value.isSaving || _state.value.saved) return
        val s = _state.value
        val petError = if (s.pet == null) "Choose a pet" else null
        val nameError = Validators.required(s.name, "Task name")
        val dateError = if (s.date == null) "Choose a date" else null
        val timeError = when {
            s.time == null && (s.reminderEnabled || s.smsReminderEnabled) -> "Choose a time for reminders"
            s.time == null -> "Choose a time"
            else -> null
        }
        val smsRecipientError = if (s.smsReminderEnabled) Validators.phone(s.smsRecipient) else null
        if (listOf(petError, nameError, dateError, timeError, smsRecipientError).any { it != null }) {
            _state.update {
                it.copy(
                    petError = petError, nameError = nameError, dateError = dateError,
                    timeError = timeError, smsRecipientError = smsRecipientError
                )
            }
            return
        }

        _state.update { it.copy(isSaving = true, formError = null) }
        viewModelScope.launch {
            var importedImagePath: String? = null
            var committed = false
            try {
                if (s.smsReminderEnabled) requireSmsConsent()
                val imagePath = when {
                    s.pendingImageUri != null -> imageStorage.persist(s.pendingImageUri).also { importedImagePath = it }
                        ?: throw IllegalStateException("The image could not be saved. Try another one.")
                    s.imageRemoved -> null
                    else -> s.existingImagePath
                }
                val previous = existing
                val task = CareTask(
                    id = previous?.id ?: 0L,
                    petId = s.pet!!.id,
                    name = s.name.trim(),
                    category = s.category.name,
                    frequency = s.frequency.name,
                    startDateEpochDay = s.date!!.toEpochDay(),
                    timeMinutes = s.time!!.hour * 60 + s.time.minute,
                    reminderEnabled = s.reminderEnabled,
                    smsReminderEnabled = s.smsReminderEnabled,
                    smsRecipient = s.smsRecipient.trim(),
                    supplies = s.supplies.trim(),
                    notes = s.notes.trim(),
                    imagePath = imagePath,
                    // Keep completion only when the schedule is unchanged; otherwise start fresh.
                    lastCompletedEpochDay = previous?.lastCompletedEpochDay?.takeIf {
                        previous.frequency == s.frequency.name && previous.startDateEpochDay == s.date.toEpochDay()
                    },
                    createdAt = previous?.createdAt ?: System.currentTimeMillis()
                )
                // Keep the database commit non-cancellable.  Once the row
                // references the imported image, cancellation must not make
                // the error path delete that image.
                withContext(NonCancellable) {
                    taskRepository.save(task)
                    committed = true
                    // Best effort: a preferences write failure must not report
                    // an already-saved task as unsaved (a retry would duplicate it).
                    if (task.smsReminderEnabled) {
                        runCatching {
                            authRepository.signedInUserId()?.let { preferences?.rememberSmsRecipient(it, task.smsRecipient) }
                        }
                    }
                }
                importedImagePath = null
                _state.update {
                    it.copy(
                        saved = true,
                        reminderPreview = s.pet?.let { p ->
                            val today = LocalDate.now()
                            ReminderNotification.preview(
                                task = task,
                                pet = p,
                                occurrence = TaskSchedule.nextOccurrenceOnOrAfter(task, today) ?: today,
                                today = today
                            )
                        }
                    )
                }
            } catch (e: CancellationException) {
                if (!committed) {
                    importedImagePath?.let { path ->
                        withContext(NonCancellable) { imageStorage.delete(path) }
                    }
                }
                throw e
            } catch (e: Exception) {
                if (!committed) {
                    importedImagePath?.let { path ->
                        withContext(NonCancellable) { imageStorage.delete(path) }
                    }
                }
                _state.update { it.copy(formError = e.userMessage()) }
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }
}
