package com.petcare.app.ui

import com.petcare.app.ui.screens.auth.accountCreatedMessage
import com.petcare.app.ui.screens.auth.welcomeBackMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthMessagesTest {
    @Test fun `welcome uses the first name`() {
        assertEquals("Welcome back, Aisha", welcomeBackMessage("Aisha Khan"))
        assertEquals("Signed in", welcomeBackMessage("  "))
    }

    @Test fun `account created greets the new user`() {
        assertEquals("Account created — welcome, Sam", accountCreatedMessage("Sam"))
        assertEquals("Account created", accountCreatedMessage(""))
    }
}
