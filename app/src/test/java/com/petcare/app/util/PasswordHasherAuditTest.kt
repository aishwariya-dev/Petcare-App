package com.petcare.app.util

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherAuditTest {

    @Test
    fun `new salts are base64 encoded sixteen-byte values and are unique`() {
        val salts = (1..12).map { PasswordHasher.newSalt() }

        assertEquals(12, salts.toSet().size)
        salts.forEach { salt ->
            assertEquals(16, Base64.getDecoder().decode(salt).size)
        }
    }

    @Test
    fun `hash is stable for a salt and verifies only the matching password`() {
        val salt = PasswordHasher.newSalt()
        val hash = PasswordHasher.hash("correct horse battery staple", salt)

        assertEquals(32, Base64.getDecoder().decode(hash).size)
        assertEquals(hash, PasswordHasher.hash("correct horse battery staple", salt))
        assertTrue(PasswordHasher.verify("correct horse battery staple", salt, hash))
        assertFalse(PasswordHasher.verify("correct horse battery stapler", salt, hash))
        assertNotEquals(hash, PasswordHasher.hash("correct horse battery staple", PasswordHasher.newSalt()))
        assertFalse(hash.contains("correct horse battery staple"))
    }

    @Test
    fun `empty passwords are hashed and verified without becoming plain text`() {
        val salt = PasswordHasher.newSalt()
        val hash = PasswordHasher.hash("", salt)

        assertTrue(PasswordHasher.verify("", salt, hash))
        assertFalse(PasswordHasher.verify(" ", salt, hash))
        assertNotEquals("", hash)
    }

    @Test
    fun `malformed persisted hashes never authenticate`() {
        val salt = PasswordHasher.newSalt()

        assertFalse(PasswordHasher.verify("secret", salt, "not-base64"))
        assertFalse(PasswordHasher.verify("secret", salt, "AA=="))
    }

    @Test
    fun `malformed persisted salts never authenticate`() {
        val validHash = PasswordHasher.hash("secret", PasswordHasher.newSalt())

        assertFalse(PasswordHasher.verify("secret", "not-base64", validHash))
        assertFalse(PasswordHasher.verify("secret", "AA==", validHash))
    }
}