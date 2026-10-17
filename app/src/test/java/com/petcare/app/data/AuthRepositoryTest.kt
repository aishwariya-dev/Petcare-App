package com.petcare.app.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.petcare.app.model.User
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AuthRepositoryTest {
    private lateinit var environment: TestEnvironment

    @Before
    fun setUp() {
        environment = TestEnvironment(ApplicationProvider.getApplicationContext<Context>())
        runBlocking { environment.auth.logout() }
    }

    @After
    fun tearDown() {
        runBlocking { environment.auth.logout() }
        environment.close()
    }

    @Test
    fun `registration normalizes email hashes password and restores session`() = runBlocking {
        val registered = environment.auth.register(
            name = " Alice Carter ",
            email = "ALICE@example.com",
            password = "secret1",
            phoneNumber = "+1 555 0100"
        ).getOrThrow()

        assertEquals("Alice Carter", registered.name)
        assertEquals("alice@example.com", registered.email)
        assertEquals("+1 555 0100", registered.phoneNumber)
        assertFalse(registered.passwordHash == "secret1")
        assertEquals(registered.id, environment.auth.signedInUserId())
        assertEquals(registered.id, environment.auth.restoreSession()!!.id)

        environment.auth.logout()
        assertNull(environment.auth.restoreSession())
        assertTrue(environment.auth.login(" alice@example.com ", "not-the-password").isFailure)
        assertNull(environment.auth.signedInUserId())

        val loggedIn = environment.auth.login(" alice@example.com ", "secret1").getOrThrow()
        assertEquals(registered.id, loggedIn.id)
    }

    @Test
    fun `registration and login enforce required and boundary credentials`() = runBlocking {
        assertTrue(environment.auth.register(" ", "valid@example.com", "secret1").isFailure)
        assertTrue(environment.auth.register("Name", "not-an-email", "secret1").isFailure)
        assertTrue(environment.auth.register("Name", "short@example.com", "12345").isFailure)
        assertTrue(environment.auth.login(" ", "secret1").isFailure)
        assertTrue(environment.auth.login("nobody@example.com", "").isFailure)

        val valid = environment.auth.register(
            "Boundary",
            "boundary@example.com",
            "123456"
        ).getOrThrow()
        assertNotNull(valid)
        assertTrue(
            environment.auth.register("Other", "BOUNDARY@example.com", "123456").isFailure
        )
    }

    @Test
    fun `optional registration phone is stored without profile validation`() = runBlocking {
        // Registration currently treats phone as optional text, while ProfileViewModel
        // validates a non-blank phone. Keep this observed asymmetry visible for review
        // rather than silently deciding whether it is a production bug.
        val user = environment.auth.register(
            name = "Phone Boundary",
            email = "phone-boundary@example.com",
            password = "secret1",
            phoneNumber = "not a phone"
        ).getOrThrow()

        assertEquals("not a phone", user.phoneNumber)
        assertEquals("not a phone", environment.database.userDao().findById(user.id)!!.phoneNumber)
    }

    @Test
    fun `deleted session row is invalidated on restore`() = runBlocking {
        val user = environment.auth.register("Session User", "session@example.com", "secret1")
            .getOrThrow()
        assertEquals(user.id, environment.preferences.currentUserId())

        environment.database.openHelper.writableDatabase.execSQL(
            "DELETE FROM users WHERE id = ?",
            arrayOf(user.id)
        )

        assertNull(environment.auth.restoreSession())
        assertNull(environment.preferences.currentUserId())
        assertNull(environment.auth.signedInUserId())
    }

    @Test
    fun `malformed persisted password is an invalid login rather than a crash`() = runBlocking {
        val id = environment.database.userDao().insert(
            User(
                name = "Corrupt",
                email = "corrupt@example.com",
                passwordHash = "not-base64",
                passwordSalt = "not-base64"
            )
        )

        val result = environment.auth.login("corrupt@example.com", "secret1")

        assertTrue(result.isFailure)
        assertEquals("No account matches that email and password.", result.exceptionOrNull()!!.message)
        assertNull(environment.auth.signedInUserId())
        assertTrue(id > 0)
    }

    @Test
    fun `profile update trims fields preserves security columns and rejects stale owner`() = runBlocking {
        val alice = environment.auth.register("Alice", "alice-profile@example.com", "secret1")
            .getOrThrow()
        val originalHash = alice.passwordHash
        val originalSalt = alice.passwordSalt

        environment.auth.updateProfile(
            user = alice,
            name = " Alice Updated ",
            phoneNumber = " +1 555 0199 ",
            photoPath = "/profile/alice.jpg"
        )
        val updated = environment.database.userDao().findById(alice.id)!!
        assertEquals("Alice Updated", updated.name)
        assertEquals("+1 555 0199", updated.phoneNumber)
        assertEquals("/profile/alice.jpg", updated.photoPath)
        assertEquals(originalHash, updated.passwordHash)
        assertEquals(originalSalt, updated.passwordSalt)

        environment.auth.logout()
        val bob = environment.auth.register("Bob", "bob-profile@example.com", "secret1").getOrThrow()
        val stale = runCatching {
            environment.auth.updateProfile(alice, "Hijacked", "000", "/hijacked.jpg")
        }
        assertTrue(stale.isFailure)
        assertEquals("Alice Updated", environment.database.userDao().findById(alice.id)!!.name)
        assertEquals("Bob", environment.database.userDao().findById(bob.id)!!.name)
    }

    @Test
    fun `blank profile name is rejected without changing the row`() = runBlocking {
        val user = environment.auth.register("Original", "blank-profile@example.com", "secret1")
            .getOrThrow()

        val result = runCatching {
            environment.auth.updateProfile(user, "   ", "555", null)
        }

        assertTrue(result.isFailure)
        assertEquals("Original", environment.database.userDao().findById(user.id)!!.name)
    }
}