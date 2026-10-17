package com.petcare.app.ui.common

/** The four screen states from the spec: loading, content, empty (content decides), error. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

fun Throwable.userMessage(): String =
    message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Please try again."
