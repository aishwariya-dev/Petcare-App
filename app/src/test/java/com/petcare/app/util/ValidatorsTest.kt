package com.petcare.app.util

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ValidatorsTest {

    @Test
    fun `required rejects blank values`() {
        assertNotNull(Validators.required("   ", "Name"))
        assertNull(Validators.required("Max", "Name"))
    }

    @Test
    fun `email must look like an address`() {
        assertNotNull(Validators.email(""))
        assertNotNull(Validators.email("not-an-email"))
        assertNull(Validators.email("owner@example.com"))
    }

    @Test
    fun `password enforces a minimum length`() {
        assertNotNull(Validators.password("abc"))
        assertNull(Validators.password("a".repeat(Validators.MIN_PASSWORD_LENGTH)))
    }

    @Test
    fun `confirm password must match`() {
        assertNotNull(Validators.confirmPassword("secret1", "secret2"))
        assertNull(Validators.confirmPassword("secret1", "secret1"))
    }

    @Test
    fun `age and weight are optional but must be sensible numbers`() {
        assertNull(Validators.age(""))
        assertNull(Validators.age("4"))
        assertNotNull(Validators.age("four"))
        assertNotNull(Validators.age("-1"))
        assertNull(Validators.weight(""))
        assertNull(Validators.weight("12.5"))
        assertNotNull(Validators.weight("heavy"))
    }

    @Test
    fun `phone accepts international formats and rejects garbage`() {
        assertNull(Validators.phone("+977 9812345678"))
        assertNull(Validators.phone("9812345678"))
        assertNotNull(Validators.phone(""))
        assertNotNull(Validators.phone("call me"))
    }
}
