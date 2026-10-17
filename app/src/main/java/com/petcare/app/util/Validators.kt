package com.petcare.app.util

/**
 * Form validation rules. Each function returns an error message, or null when valid.
 * No Android dependencies so the rules can be unit tested on the JVM.
 */
object Validators {

    // Keep the relaxed, useful address syntax, but enforce dot and domain-label
    // boundaries that the simple character-class expression cannot express.
    private val EMAIL_REGEX = Regex(
        "^[A-Za-z0-9_%+\\-]+(?:\\.[A-Za-z0-9_%+\\-]+)*@" +
            "(?:[A-Za-z0-9](?:[A-Za-z0-9\\-]*[A-Za-z0-9])?\\.)+" +
            "[A-Za-z]{2,}$"
    )
    private val PHONE_REGEX = Regex("^\\+?[0-9][0-9 \\-()]{5,}$")

    const val MIN_PASSWORD_LENGTH = 6

    fun required(value: String, fieldLabel: String): String? =
        if (value.isBlank()) "$fieldLabel is required" else null

    fun email(value: String): String? = when {
        value.isBlank() -> "Email is required"
        !EMAIL_REGEX.matches(value.trim()) -> "Enter a valid email address"
        else -> null
    }

    fun password(value: String): String? = when {
        value.isEmpty() -> "Password is required"
        value.length < MIN_PASSWORD_LENGTH -> "Password must be at least $MIN_PASSWORD_LENGTH characters"
        else -> null
    }

    fun confirmPassword(password: String, confirm: String): String? = when {
        confirm.isEmpty() -> "Please confirm your password"
        password != confirm -> "Passwords do not match"
        else -> null
    }

    /** Optional integer age between 0 and 100. */
    fun age(value: String): String? {
        if (value.isBlank()) return null
        val n = value.trim().toIntOrNull() ?: return "Age must be a whole number"
        return if (n < 0 || n > 100) "Age must be between 0 and 100" else null
    }

    /** Optional positive weight in kilograms. */
    fun weight(value: String): String? {
        if (value.isBlank()) return null
        val n = value.trim().toDoubleOrNull() ?: return "Weight must be a number"
        return if (!n.isFinite() || n <= 0 || n > 500) "Enter a realistic weight in kg" else null
    }

    fun phone(value: String): String? = when {
        value.isBlank() -> "Phone number is required"
        !PHONE_REGEX.matches(value.trim()) -> "Enter a valid phone number"
        else -> null
    }
}
