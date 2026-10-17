package com.petcare.app.ui.screens.auth

/** Short confirmations shown in the app snackbar after authentication. */
fun welcomeBackMessage(name: String): String {
    val first = name.trim().substringBefore(' ')
    return if (first.isBlank()) "Signed in" else "Welcome back, $first"
}

fun accountCreatedMessage(name: String): String {
    val first = name.trim().substringBefore(' ')
    return if (first.isBlank()) "Account created" else "Account created — welcome, $first"
}
