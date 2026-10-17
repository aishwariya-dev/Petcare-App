package com.petcare.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class UserAvatarTest {
    @Test
    fun `initials use the authenticated name and remain accessible without a photo`() {
        assertEquals("ES", userInitials("  Emily Stone  "))
        assertEquals("M", userInitials("Max"))
        assertEquals("?", userInitials("   "))
    }
}