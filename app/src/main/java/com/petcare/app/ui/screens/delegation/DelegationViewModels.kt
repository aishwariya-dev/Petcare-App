package com.petcare.app.ui.screens.delegation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petcare.app.data.repository.PetRepository
import com.petcare.app.data.repository.TaskRepository
import com.petcare.app.model.CareTask
import com.petcare.app.model.Pet
import com.petcare.app.navigation.Routes
import com.petcare.app.ui.common.UiState
import com.petcare.app.ui.common.userMessage
import com.petcare.app.util.DelegationMessageBuilder
import com.petcare.app.util.DelegationOptions
import com.petcare.app.util.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class DelegateFormState(
    val options: DelegationOptions = DelegationOptions(),
    val phone: String = "",
    val phoneError: String? = null,
    val optionsError: String? = null
)

/** Screen 20. */
class DelegateViewModel(
    petRepository: PetRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val petId: Long = checkNotNull(savedStateHandle[Routes.ARG_PET_ID])

    val pet: StateFlow<UiState<Pet?>> = petRepository.observePet(petId)
        .map<Pet?, UiState<Pet?>> { UiState.Success(it) }
        .catch { emit(UiState.Error(it.userMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    private val _form = MutableStateFlow(DelegateFormState())
    val form: StateFlow<DelegateFormState> = _form.asStateFlow()

    fun setOptions(transform: DelegationOptions.() -> DelegationOptions) =
        _form.update { it.copy(options = it.options.transform(), optionsError = null) }

    fun onPhoneChange(v: String) = _form.update { it.copy(phone = v, phoneError = null) }

    /** Returns the validated (flags, phone) pair, or null after setting errors. */
    fun validate(): Pair<String, String>? {
        val s = _form.value
        val phoneError = Validators.phone(s.phone)
        val optionsError = if (!s.options.hasSelection) "Choose at least one thing to share" else null
        if (phoneError != null || optionsError != null) {
            _form.update { it.copy(phoneError = phoneError, optionsError = optionsError) }
            return null
        }
        return s.options.encode() to s.phone.trim()
    }
}

data class PreviewData(val pet: Pet, val phone: String, val message: String)

/** Screen 21. */
class SmsPreviewViewModel(
    petRepository: PetRepository,
    taskRepository: TaskRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val petId: Long = checkNotNull(savedStateHandle[Routes.ARG_PET_ID])
    private val options = DelegationOptions.decode(savedStateHandle[Routes.ARG_FLAGS])
    val phone: String = savedStateHandle.get<String>(Routes.ARG_PHONE).orEmpty()

    val state: StateFlow<UiState<PreviewData?>> = combine<Pet?, List<CareTask>, UiState<PreviewData?>>(
        petRepository.observePet(petId),
        taskRepository.observeTasksForPet(petId)
    ) { pet, tasks ->
        val data = if (pet == null) null else PreviewData(pet, phone, DelegationMessageBuilder.build(pet, tasks, options))
        UiState.Success(data)
    }
        .catch { emit(UiState.Error(it.userMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)
}
