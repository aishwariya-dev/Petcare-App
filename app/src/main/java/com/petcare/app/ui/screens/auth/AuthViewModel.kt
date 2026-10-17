package com.petcare.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petcare.app.data.repository.AuthRepository
import com.petcare.app.ui.common.userMessage
import com.petcare.app.util.Validators
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val formError: String? = null,
    val isSubmitting: Boolean = false,
    val success: Boolean = false,
    /** Display name of the account that just signed in, for the welcome message. */
    val signedInName: String = ""
)

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, emailError = null, formError = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, passwordError = null, formError = null) }

    fun submit() {
        if (_state.value.isSubmitting || _state.value.success) return
        val s = _state.value
        val emailError = Validators.email(s.email)
        val passwordError = if (s.password.isEmpty()) "Password is required" else null
        if (emailError != null || passwordError != null) {
            _state.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }
        _state.update { it.copy(isSubmitting = true, formError = null) }
        viewModelScope.launch {
            try {
                authRepository.login(s.email, s.password)
                    .onSuccess { user -> _state.update { it.copy(success = true, signedInName = user.name) } }
                    .onFailure { e -> _state.update { it.copy(formError = e.userMessage()) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(formError = e.userMessage()) }
            } finally {
                _state.update { it.copy(isSubmitting = false) }
            }
        }
    }
}

data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val phone: String = "",
    val nameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmError: String? = null,
    val formError: String? = null,
    val isSubmitting: Boolean = false,
    val success: Boolean = false,
    /** Display name of the account that just signed in, for the welcome message. */
    val signedInName: String = ""
)

class RegisterViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun onNameChange(value: String) = _state.update { it.copy(name = value, nameError = null, formError = null) }
    fun onEmailChange(value: String) = _state.update { it.copy(email = value, emailError = null, formError = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, passwordError = null, confirmError = null, formError = null) }
    fun onConfirmChange(value: String) = _state.update { it.copy(confirmPassword = value, confirmError = null, formError = null) }
    fun onPhoneChange(value: String) = _state.update { it.copy(phone = value) }

    fun submit() {
        if (_state.value.isSubmitting || _state.value.success) return
        val s = _state.value
        val nameError = Validators.required(s.name, "Name")
        val emailError = Validators.email(s.email)
        val passwordError = Validators.password(s.password)
        val confirmError = Validators.confirmPassword(s.password, s.confirmPassword)
        if (listOf(nameError, emailError, passwordError, confirmError).any { it != null }) {
            _state.update {
                it.copy(nameError = nameError, emailError = emailError,
                    passwordError = passwordError, confirmError = confirmError)
            }
            return
        }
        _state.update { it.copy(isSubmitting = true, formError = null) }
        viewModelScope.launch {
            try {
                authRepository.register(s.name, s.email, s.password, s.phone)
                    .onSuccess { user -> _state.update { it.copy(success = true, signedInName = user.name) } }
                    .onFailure { e -> _state.update { it.copy(formError = e.userMessage()) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(formError = e.userMessage()) }
            } finally {
                _state.update { it.copy(isSubmitting = false) }
            }
        }
    }
}
