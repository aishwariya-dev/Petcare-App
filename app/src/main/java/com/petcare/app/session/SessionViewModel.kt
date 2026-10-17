package com.petcare.app.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petcare.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SessionState { SIGNED_IN, SIGNED_OUT }

/**
 * Seeds the demo household (idempotent) and restores the saved session once
 * per process while the system splash is still on screen. [state] stays null
 * until both finish, so the first frame the user sees is already the right
 * screen and the demo login works on a clean install.
 */
class SessionViewModel(
    auth: AuthRepository,
    seed: suspend () -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow<SessionState?>(null)
    val state: StateFlow<SessionState?> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { seed() }
            val restored = runCatching { auth.restoreSession() }.getOrNull()
            _state.value = if (restored != null) SessionState.SIGNED_IN else SessionState.SIGNED_OUT
        }
    }
}
