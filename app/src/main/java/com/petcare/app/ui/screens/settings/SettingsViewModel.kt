package com.petcare.app.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petcare.app.data.preferences.UserPreferences
import com.petcare.app.data.repository.AuthRepository
import com.petcare.app.data.repository.PetRepository
import com.petcare.app.model.ThemeMode
import com.petcare.app.model.User
import com.petcare.app.ui.common.userMessage
import com.petcare.app.util.ImageStorage
import com.petcare.app.util.Validators
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Screens 23 + 24. */
class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val preferences: UserPreferences,
    petRepository: PetRepository? = null
) : ViewModel() {

    val user: StateFlow<User?> = authRepository.currentUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val themeMode: StateFlow<ThemeMode> = preferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    @OptIn(ExperimentalCoroutinesApi::class)
    val petCount: StateFlow<Int> = authRepository.currentUser
        .flatMapLatest { user ->
            if (user == null || petRepository == null) flowOf(0)
            else petRepository.observePets(user.id).map { it.size }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Account-scoped master switch; default on for every account. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val remindersEnabled: StateFlow<Boolean> = authRepository.currentUser
        .flatMapLatest { user -> if (user == null) flowOf(true) else preferences.remindersEnabled(user.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val userId = authRepository.signedInUserId() ?: return@launch
            // Written under the SMS authorization gate so an in-flight automatic
            // SMS either finishes before the switch changes or sees the new value.
            authRepository.smsAuthorizationGate.withLock {
                preferences.setRemindersEnabled(userId, enabled)
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onDone()
        }
    }
}

data class ProfileState(
    val user: User? = null,
    val name: String = "",
    val nameError: String? = null,
    val phone: String = "",
    val phoneError: String? = null,
    /** Persisted photo path from the User entity (shown while idle). */
    val persistedPhotoPath: String? = null,
    /** URI picked from the photo picker but not yet saved to disk. */
    val pendingPhotoUri: Uri? = null,
    val isPhotoRemoved: Boolean = false,
    val isSaving: Boolean = false,
    val loaded: Boolean = false
) {
    val dirty: Boolean get() = user != null &&
        (name.trim() != user.name || phone.trim() != user.phoneNumber ||
            pendingPhotoUri != null || isPhotoRemoved)
    /** The path to show in the UI — the persisted one unless it's been removed. */
    val visiblePhotoPath: String?
        get() = if (isPhotoRemoved) null else persistedPhotoPath
}

data class PasswordChangeState(
    val current: String = "",
    val currentError: String? = null,
    val new: String = "",
    val newError: String? = null,
    val confirm: String = "",
    val confirmError: String? = null,
    val isSaving: Boolean = false
) {
    val canSubmit: Boolean get() = !isSaving && current.isNotEmpty() && new.isNotEmpty() && confirm.isNotEmpty()
}

class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val imageStorage: ImageStorage
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    private val _password = MutableStateFlow(PasswordChangeState())
    val password: StateFlow<PasswordChangeState> = _password.asStateFlow()

    fun onCurrentPasswordChange(v: String) = _password.update { it.copy(current = v, currentError = null) }
    fun onNewPasswordChange(v: String) = _password.update { it.copy(new = v, newError = null) }
    fun onConfirmPasswordChange(v: String) = _password.update { it.copy(confirm = v, confirmError = null) }

    fun changePassword(onChanged: () -> Unit) {
        val form = _password.value
        if (form.isSaving) return
        val currentError = if (form.current.isEmpty()) "Enter your current password" else null
        val newError = Validators.password(form.new)
        val confirmError = if (form.confirm != form.new) "Passwords do not match" else null
        if (currentError != null || newError != null || confirmError != null) {
            _password.update {
                it.copy(currentError = currentError, newError = newError, confirmError = confirmError)
            }
            return
        }
        _password.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = authRepository.changePassword(form.current, form.new)
            result.onSuccess {
                _password.value = PasswordChangeState()
                onChanged()
            }.onFailure { error ->
                val message = error.userMessage()
                _password.update {
                    it.copy(
                        isSaving = false,
                        currentError = if (message.contains("current password", ignoreCase = true)) message else null,
                        newError = if (message.contains("current password", ignoreCase = true)) null else message
                    )
                }
            }
        }
    }

    init {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                val previous = _state.value
                if (previous.user?.id != user?.id) {
                    // A session switch must not carry the previous account's
                    // draft fields or temporary capture into this account.
                    imageStorage.discardCapture(previous.pendingPhotoUri)
                    _state.value = ProfileState(
                        user = user,
                        name = user?.name.orEmpty(),
                        phone = user?.phoneNumber.orEmpty(),
                        persistedPhotoPath = user?.photoPath,
                        loaded = true
                    )
                } else {
                    _state.update { s ->
                        s.copy(
                            user = user,
                            // Keep unsaved edits when Room emits the same
                            // account after a successful profile update.
                            persistedPhotoPath = user?.photoPath,
                            loaded = true
                        )
                    }
                }
            }
        }
    }

    fun onNameChange(v: String) = _state.update { it.copy(name = v, nameError = null) }
    fun onPhoneChange(v: String) = _state.update { it.copy(phone = v, phoneError = null) }

    fun onPhotoPicked(uri: Uri) {
        imageStorage.discardCapture(_state.value.pendingPhotoUri?.takeIf { it != uri })
        _state.update {
            it.copy(pendingPhotoUri = uri, isPhotoRemoved = false)
        }
    }

    fun onPhotoRemoved() {
        imageStorage.discardCapture(_state.value.pendingPhotoUri)
        _state.update {
            it.copy(pendingPhotoUri = null, isPhotoRemoved = true)
        }
    }

    /** Called for an explicit back/cancel; configuration recreation must not call this. */
    fun discardPendingPhoto() {
        imageStorage.discardCapture(_state.value.pendingPhotoUri)
        _state.update {
            it.copy(
                pendingPhotoUri = null,
                // Removing a photo only changes the unsaved draft.  Backing
                // out must not leave a retained ViewModel showing a deletion.
                isPhotoRemoved = false
            )
        }
    }

    fun save(onSaved: () -> Unit, onError: (String) -> Unit) {
        if (_state.value.isSaving) return
        val s = _state.value
        val user = s.user ?: return
        val nameError = Validators.required(s.name, "Name")
        val phoneError = s.phone.trim().takeIf { it.isNotEmpty() }?.let { Validators.phone(it) }
        if (nameError != null || phoneError != null) {
            _state.update { it.copy(nameError = nameError, phoneError = phoneError) }
            return
        }
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            var importedPhotoPath: String? = null
            var committed = false
            try {
                // Persist a newly picked photo
                val newPhotoPath: String? = when {
                    s.pendingPhotoUri != null -> imageStorage.persist(s.pendingPhotoUri).also {
                        importedPhotoPath = it
                    } ?: throw IllegalStateException("The photo could not be saved. Try another image.")
                    s.isPhotoRemoved -> null
                    else -> user.photoPath   // unchanged
                }
                // The repository validates the active owner and updates the
                // complete profile in one database operation. Old files are
                // deliberately not deleted here: a failed transaction must
                // leave the existing profile usable.
                // Keep the database commit and the ownership check together.
                // If this coroutine is cancelled after Room commits, the
                // imported image is now referenced and must not be cleaned up.
                withContext(NonCancellable) {
                    authRepository.updateProfile(
                        user = user,
                        name = s.name.trim(),
                        phoneNumber = s.phone.trim(),
                        photoPath = newPhotoPath
                    )
                    committed = true
                }
                // The new path is now committed. Never delete it if a later UI
                // callback fails; only dispose of the replaced file after save.
                importedPhotoPath = null
                withContext(NonCancellable) {
                    user.photoPath?.takeIf { it != newPhotoPath }?.let { imageStorage.delete(it) }
                }
                _state.update { it.copy(isSaving = false, persistedPhotoPath = newPhotoPath,
                    pendingPhotoUri = null, isPhotoRemoved = false,
                    phone = s.phone.trim()) }
                onSaved()
            } catch (e: CancellationException) {
                if (!committed) {
                    importedPhotoPath?.let { path ->
                        withContext(NonCancellable) { imageStorage.delete(path) }
                    }
                }
                throw e
            } catch (e: Exception) {
                if (!committed) {
                    importedPhotoPath?.let { path ->
                        withContext(NonCancellable) { imageStorage.delete(path) }
                    }
                }
                _state.update { it.copy(isSaving = false) }
                onError(e.userMessage())
            }
        }
    }
}
