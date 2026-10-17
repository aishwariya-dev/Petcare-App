package com.petcare.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsAuditTest {

    @Test
    fun `required reports blank values and preserves the supplied label`() {
        assertValidatorCases(
            label = "required",
            cases = listOf(
                "" to "Pet name is required",
                "Miso" to null
            )
        ) { value -> Validators.required(value, "Pet name") }
        assertEquals(
            "Phone number is required",
            Validators.required(" \t\n", "Phone number")
        )
    }

    @Test
    fun `email accepts useful addresses and rejects malformed boundaries`() {
        assertValidatorCases(
            label = "email",
            cases = listOf(
                "owner@example.com" to null,
                "person+tag@sub.example.co.uk" to null,
                "A9@x.io" to null,
                "  owner@example.com  " to null,
                "" to "Email is required",
                "   " to "Email is required",
                " \t" to "Email is required",
                "owner" to "Enter a valid email address",
                "owner@" to "Enter a valid email address",
                "owner@example.c" to "Enter a valid email address",
                "owner@@example.com" to "Enter a valid email address",
                "a..b@example.com" to "Enter a valid email address",
                "a@example..com" to "Enter a valid email address",
                "a@-example.com" to "Enter a valid email address",
                "a@example-.com" to "Enter a valid email address",
                "a@.example.com" to "Enter a valid email address"
            ),
            validator = Validators::email
        )
    }

    @Test
    fun `password and confirmation cover empty minimum and mismatch cases`() {
        assertValidatorCases(
            label = "password",
            cases = listOf(
                "" to "Password is required",
                "12345" to "Password must be at least 6 characters",
                "123456" to null,
                "pässw0rd" to null
            ),
            validator = Validators::password
        )

        val confirmationCases = listOf(
            Triple("secret", "", "Please confirm your password"),
            Triple("secret", "Secret", "Passwords do not match"),
            Triple("secret", "secret", null)
        )
        val confirmationMismatches = confirmationCases.mapNotNull { (password, confirm, expected) ->
            val actual = Validators.confirmPassword(password, confirm)
            when {
                actual == expected -> null
                expected != null && actual == null ->
                    "Unexpectedly accepted input password='$password', confirmation='$confirm': " +
                        "expected '$expected', got null"
                else ->
                    "Input password='$password', confirmation='$confirm': " +
                        "expected '${expected ?: "valid"}', got '$actual'"
            }
        }
        assertTrue(
            "confirmPassword validator mismatches:\n${confirmationMismatches.joinToString("\n")}",
            confirmationMismatches.isEmpty()
        )
    }

    @Test
    fun `age accepts optional values only within inclusive limits`() {
        assertValidatorCases(
            label = "age",
            cases = listOf(
                "" to null,
                " " to null,
                "0" to null,
                " 100 " to null,
                "42" to null,
                "-1" to "Age must be between 0 and 100",
                "101" to "Age must be between 0 and 100",
                "4.5" to "Age must be a whole number",
                "four" to "Age must be a whole number",
                "1e2" to "Age must be a whole number",
                "999999999999999999999" to "Age must be a whole number"
            ),
            validator = Validators::age
        )
    }

    @Test
    fun `weight accepts optional positive values through five hundred kilograms`() {
        assertValidatorCases(
            label = "weight",
            cases = listOf(
                "" to null,
                " " to null,
                "0.001" to null,
                "12.5" to null,
                "500" to null,
                " 42 " to null,
                "0" to "Enter a realistic weight in kg",
                "-0.1" to "Enter a realistic weight in kg",
                "500.01" to "Enter a realistic weight in kg",
                "heavy" to "Weight must be a number",
                "NaN" to "Enter a realistic weight in kg",
                "Infinity" to "Enter a realistic weight in kg"
            ),
            validator = Validators::weight
        )
    }

    @Test
    fun `phone accepts digit based international forms and rejects malformed input`() {
        assertValidatorCases(
            label = "phone",
            cases = listOf(
                "+977 9812345678" to null,
                "9812345678" to null,
                "+1 (555) 010-0200" to null,
                "123456" to null,
                "" to "Phone number is required",
                "     " to "Phone number is required",
                "12345" to "Enter a valid phone number",
                "call me" to "Enter a valid phone number",
                "12ab3456" to "Enter a valid phone number",
                "+1     " to "Enter a valid phone number"
            ),
            validator = Validators::phone
        )
    }

    private fun assertValidatorCases(
        label: String,
        cases: List<Pair<String, String?>>,
        validator: (String) -> String?
    ) {
        val mismatches = cases.mapNotNull { (input, expected) ->
            val actual = validator(input)
            when {
                actual == expected -> null
                expected != null && actual == null ->
                    "Unexpectedly accepted input '$input': expected '$expected', got null"
                else ->
                    "Input '$input': expected '${expected ?: "valid"}', got '$actual'"
            }
        }

        assertTrue(
            "$label validator mismatches:\n${mismatches.joinToString("\n")}",
            mismatches.isEmpty()
        )
    }
}