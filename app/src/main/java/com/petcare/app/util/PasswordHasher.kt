package com.petcare.app.util

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PBKDF2 password hashing so plain-text passwords are never stored. */
object PasswordHasher {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    fun newSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun hash(password: String, salt: String): String {
        val spec = PBEKeySpec(
            password.toCharArray(),
            Base64.getDecoder().decode(salt),
            ITERATIONS,
            KEY_LENGTH_BITS
        )
        val key = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        spec.clearPassword()
        return Base64.getEncoder().encodeToString(key)
    }

    fun verify(password: String, salt: String, expectedHash: String): Boolean {
        return try {
            val saltBytes = Base64.getDecoder().decode(salt)
            val expected = Base64.getDecoder().decode(expectedHash)
            // Persisted credentials must have the format produced by this
            // helper. In particular, do not let a short salt silently become
            // a valid PBKDF2 input.
            if (saltBytes.size != SALT_BYTES || expected.size != KEY_LENGTH_BITS / 8) {
                return false
            }
            val actual = Base64.getDecoder().decode(hash(password, salt))
            MessageDigest.isEqual(actual, expected)
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: java.security.GeneralSecurityException) {
            false
        }
    }
}
