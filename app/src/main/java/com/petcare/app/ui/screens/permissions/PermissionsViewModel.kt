package com.petcare.app.ui.screens.permissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petcare.app.data.preferences.UserPreferences
import com.petcare.app.data.repository.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Account-scoped SMS consent plus the device-level "intro seen" flag. */
class PermissionsViewModel(
    private val authRepository: AuthRepository,
    private val preferences: UserPreferences
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val smsConsent: StateFlow<Boolean> = authRepository.currentUser
        .flatMapLatest { user -> if (user == null) flowOf(false) else preferences.smsConsent(user.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun grantSmsConsent() {
        viewModelScope.launch {
            val userId = authRepository.signedInUserId() ?: return@launch
            authRepository.smsAuthorizationGate.withLock { preferences.setSmsConsent(userId) }
        }
    }

    fun markIntroSeen() {
        viewModelScope.launch { preferences.setPermissionsIntroSeen() }
    }
}
