package com.petcare.app.ui.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Single snackbar host for the whole app so transient feedback ("Task saved",
 * "Task deleted — Undo") survives navigation between screens.
 */
class FeedbackController(
    private val hostState: SnackbarHostState,
    private val scope: CoroutineScope
) {
    fun show(
        message: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        onDismissed: (() -> Unit)? = null
    ) {
        scope.launch {
            hostState.currentSnackbarData?.dismiss()
            val result = hostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                withDismissAction = false,
                duration = if (actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short
            )
            when (result) {
                SnackbarResult.ActionPerformed -> onAction?.invoke()
                SnackbarResult.Dismissed -> onDismissed?.invoke()
            }
        }
    }
}

val LocalFeedback = staticCompositionLocalOf<FeedbackController> {
    error("FeedbackController not provided")
}
