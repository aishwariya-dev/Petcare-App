package com.petcare.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.petcare.app.PetCareApp
import com.petcare.app.ui.common.userMessage
import com.petcare.app.ui.components.ErrorState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private sealed interface SessionResolution {
    data object Resolving : SessionResolution
    data object SignedOut : SessionResolution
    data class SignedIn(val userId: Long) : SessionResolution
    data class Failed(val message: String) : SessionResolution
}

/**
 * A restored NavController can start on any destination, bypassing Splash.
 * An absent session is a resolved signed-out state, never an endless loader.
 */
@Composable
internal fun SessionGuard(navController: NavHostController, route: String?) {
    val auth = (LocalContext.current.applicationContext as PetCareApp).container.authRepository
    var retry by remember { mutableIntStateOf(0) }
    val source = remember(auth, retry) {
        auth.currentUser.map { user ->
            if (user == null) SessionResolution.SignedOut
            else SessionResolution.SignedIn(user.id)
        }.map { it as SessionResolution }
            .catch { emit(SessionResolution.Failed(it.userMessage())) }
    }
    val session by source.collectAsStateWithLifecycle(SessionResolution.Resolving)
    // A failed redirect belongs only to this route and this resolved session.
    // Do not let it cover Home after a later successful login.
    var redirectError by remember(route, session) { mutableStateOf<String?>(null) }
    // Navigation is synchronous but composition of the replaced NavHost entry
    // is not. Retire the protected entry's error as soon as it redirects so its
    // sibling Surface cannot intercept Welcome before that composition arrives.
    var redirectingToWelcome by remember(route) { mutableStateOf(false) }
    val protected = route != null && route !in publicRoutes

    LaunchedEffect(route, session, retry) {
        if (protected && session == SessionResolution.SignedOut) {
            try {
                // Re-check storage under the session lock. A login callback may
                // navigate before this collector receives its signed-in emission.
                if (auth.restoreSession() == null) {
                    withContext(Dispatchers.Main.immediate) {
                        // The error belongs to the protected entry. Clear it before
                        // replacing that entry so its full-screen Surface cannot
                        // remain above the Welcome destination.
                        redirectError = null
                        redirectingToWelcome = true
                        navController.resetTo(Routes.WELCOME)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                withContext(Dispatchers.Main.immediate) {
                    redirectingToWelcome = false
                    redirectError = error.userMessage()
                }
            }
        }
    }
    val message = (session as? SessionResolution.Failed)?.message ?:
        redirectError.takeIf { session == SessionResolution.SignedOut }
    if (protected && !redirectingToWelcome && message != null) {
        androidx.compose.material3.Surface {
            ErrorState(message, onRetry = {
                redirectError = null
                retry++
            })
        }
    }
}

private val publicRoutes = setOf(Routes.WELCOME, Routes.LOGIN, Routes.REGISTER)