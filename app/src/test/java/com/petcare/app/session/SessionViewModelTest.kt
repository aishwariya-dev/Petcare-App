package com.petcare.app.session

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.petcare.app.data.TestEnvironment
import com.petcare.app.data.awaitCondition
import com.petcare.app.data.local.DatabaseSeeder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A clean install has an empty database. The splash-time session restore must
 * seed the demo household first, otherwise the advertised demo login fails.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SessionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var environment: TestEnvironment

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        environment = TestEnvironment(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() {
        environment.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `clean install seeds the demo household before resolving a signed-out state`() = runTest(dispatcher) {
        assertNull(environment.database.userDao().findByEmail(DatabaseSeeder.DEMO_EMAIL))

        val viewModel = SessionViewModel(environment.auth) { DatabaseSeeder.seed(environment.database) }
        assertNull(viewModel.state.value)
        awaitCondition(description = "session state") { viewModel.state.value != null }

        assertEquals(SessionState.SIGNED_OUT, viewModel.state.value)
        assertNotNull(environment.database.userDao().findByEmail(DatabaseSeeder.DEMO_EMAIL))
        assertTrue(environment.auth.login(DatabaseSeeder.DEMO_EMAIL, DatabaseSeeder.DEMO_PASSWORD).isSuccess)
    }

    @Test
    fun `a seeding failure still resolves the session instead of hanging on the splash`() = runTest(dispatcher) {
        val viewModel = SessionViewModel(environment.auth) { error("disk full") }
        awaitCondition(description = "session state") { viewModel.state.value != null }
        assertEquals(SessionState.SIGNED_OUT, viewModel.state.value)
    }
}
