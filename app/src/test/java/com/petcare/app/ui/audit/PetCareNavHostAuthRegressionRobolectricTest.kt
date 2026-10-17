package com.petcare.app.ui.audit

import android.app.Application
import android.content.ComponentName
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.petcare.app.data.local.UserDao
import com.petcare.app.model.User
import com.petcare.app.navigation.PetCareNavHost
import com.petcare.app.navigation.Routes
import com.petcare.app.ui.components.LoadingState
import com.petcare.app.ui.theme.PetCareTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Auth/navigation regressions that require a captured rememberNavController.
 *
 * These cases intentionally drive a real PetCareNavHost, including direct
 * navigation to a main destination while the session is null. They protect
 * restored/deep-link-like entry as well as the top-level tab back-stack
 * contract after login and logout.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PetCareNavHostAuthRegressionRobolectricTest {

    private val composeRule = createAndroidComposeRule<NavHostAuditActivity>()

    @get:Rule
    val rule: TestRule = RuleChain
        .outerRule(RegisterNavHostRegressionActivityRule())
        .around(composeRule)

    private lateinit var activity: NavHostAuditActivity
    private var capturedController: NavHostController? = null

    @Before
    fun setUp() {
        activity = composeRule.activity
        runBlocking { activity.navAuth.logout() }
    }

    @After
    fun tearDown() {
        composeRule.runOnIdle { activity.viewModelStore.clear() }
        repeat(3) { shadowOf(Looper.getMainLooper()).idle() }
        activity.closeNavResources()
    }

    @Test
    fun `logged out direct main destination redirects to welcome`() {
        showNavHost()
        awaitDisplayed("Welcome to PetCare")

        // This is the route shape a restored/deep-link launch can present
        // without passing through SplashScreen. The null session must not
        // expose the authenticated destination or leave it loading forever.
        val navController = navController()
        composeRule.runOnIdle { navController.navigate(Routes.HOME) }
        awaitDisplayed("Welcome to PetCare")
        assertEquals(Routes.WELCOME, navController.currentDestination?.route)

        composeRule.runOnIdle { navController.navigate(Routes.PETS) }
        awaitDisplayed("Welcome to PetCare")
        assertEquals(Routes.WELCOME, navController.currentDestination?.route)
    }

    @Test
    fun `welcome login action opens the email form`() {
        showNavHost()
        awaitDisplayed("Welcome to PetCare")
        composeRule.runOnIdle { navController().navigate(Routes.HOME) }
        awaitDisplayed("Welcome to PetCare")

        composeRule.onNode(hasTextAndClick("Log in"))
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        awaitRoute(Routes.LOGIN)
        awaitDisplayed("Email *")
    }

    @Test
    fun `repeated top level tabs keep one tab entry and back returns home`() {
        signIn("Tabs Regression User", "tabs-regression@test.local")
        showNavHost()
        awaitDisplayed(", Tabs", substring = true)

        repeat(3) {
            listOf("Pets", "Tasks", "Settings", "Home").forEach(::tapTab)
        }
        awaitDisplayed(", Tabs", substring = true)
        assertEquals(1, topLevelEntries(navController()))

        // A final tab change must not make the previous tab the back target.
        tapTab("Pets")
        awaitDisplayed("No pets yet")
        tapTab("Tasks")
        awaitDisplayed("Add a pet first")
        tapTab("Settings")
        awaitRoute(Routes.SETTINGS)
        awaitDisplayed("ACCOUNT")
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        awaitDisplayed(", Tabs", substring = true)
        assertEquals(Routes.HOME, navController().currentDestination?.route)
        assertEquals(1, topLevelEntries(navController()))
    }

    @Test
    fun `logout and login clear saved tab back stacks`() {
        signIn("Logout Regression User", "logout-regression@test.local")
        showNavHost()
        awaitDisplayed(", Logout", substring = true)

        tapTab("Pets")
        awaitDisplayed("No pets yet")
        tapTab("Tasks")
        awaitDisplayed("Add a pet first")
        tapTab("Settings")
        awaitRoute(Routes.SETTINGS)
        awaitDisplayed("ACCOUNT")
        logOut()
        awaitDisplayed("Welcome to PetCare")
        assertEquals(Routes.WELCOME, navController().currentDestination?.route)

        logIn("logout-regression@test.local")
        awaitDisplayed(", Logout", substring = true)
        assertEquals(Routes.HOME, navController().currentDestination?.route)
        assertEquals(1, topLevelEntries(navController()))

        // The old Pets/Tasks entries must not be restored after a fresh login.
        tapTab("Pets")
        awaitDisplayed("No pets yet")
        tapTab("Home")
        awaitDisplayed(", Logout", substring = true)
        assertEquals(Routes.HOME, navController().currentDestination?.route)
        assertEquals(1, topLevelEntries(navController()))
    }

    @Test
    fun `loading animation exposes indeterminate background progress semantics`() {
        composeRule.setContent { LoadingAuditContent() }

        val animation = composeRule.onNodeWithTag("loading-animation")
        animation
            .assertIsDisplayed()
            .assert(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertContentDescriptionContains("Loading in background")
        composeRule.onNodeWithText("Loading…").assertIsDisplayed()
    }

    private fun signIn(name: String, email: String) {
        runBlocking {
            val user = activity.navAuth.register(name, email, "secret1")
                .getOrThrow()
            activity.navPreferences.setPermissionsIntroSeen()
        }
    }

    private fun logIn(email: String) {
        composeRule.onNode(hasTextAndClick("Log in"))
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        awaitDisplayed("Email *")
        composeRule.onNodeWithText("Email *")
            .performScrollTo()
            .assertIsDisplayed()
            .performTextInput(email)
        composeRule.onNodeWithText("Password *")
            .performScrollTo()
            .assertIsDisplayed()
            .performTextInput("secret1")
        composeRule.onNode(hasTextAndClick("Log in"))
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
    }

    private fun logOut() {
        composeRule.onNodeWithText("Log out").performScrollTo().assertIsDisplayed().performClick()
        awaitDisplayed("Log out?")
        val buttons = composeRule.onAllNodesWithText("Log out")
        buttons[buttons.fetchSemanticsNodes().lastIndex]
            .assertIsDisplayed()
            .performClick()
    }

    private fun tapTab(label: String) {
        composeRule.onNode(hasTextAndClick(label))
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
    }

    private fun showNavHost() {
        capturedController = null
        composeRule.setContent {
            val navController = rememberNavController()
            SideEffect { capturedController = navController }
            PetCareTheme { PetCareNavHost(startDestination = rememberStartDestination(), navController = navController) }
        }
    }

    private fun navController(): NavHostController =
        checkNotNull(capturedController) { "rememberNavController was not captured" }

    private fun topLevelEntries(controller: NavHostController): Int =
        controller.currentBackStack.value.count { entry ->
            Routes.canonicalTopLevel(entry.destination.route) != null
        }

    private fun awaitDisplayed(text: String, substring: Boolean = false) {
        composeRule.waitUntil(8_000) {
            shadowOf(Looper.getMainLooper()).idle()
            runCatching {
                composeRule.onNodeWithText(text, substring = substring).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.waitForIdle()
    }

    private fun awaitRoute(route: String) {
        composeRule.waitUntil(8_000) {
            shadowOf(Looper.getMainLooper()).idle()
            navController().currentDestination?.route == route
        }
    }

    @Composable
    private fun LoadingAuditContent() {
        PetCareTheme { LoadingState() }
    }
}

private fun hasTextAndClick(text: String) = hasClickAction().and(hasText(text))

private class RegisterNavHostRegressionActivityRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                val context = ApplicationProvider.getApplicationContext<Application>()
                val packageManager = shadowOf(context.packageManager)
                val component = ComponentName(context, NavHostAuditActivity::class.java)
                packageManager.addActivityIfNotPresent(component)
                try {
                    base.evaluate()
                } finally {
                    packageManager.removeActivity(component)
                }
            }
        }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PetCareSessionGuardStaleSessionRobolectricTest {

    private val composeRule = createAndroidComposeRule<NavHostAuditActivity>()

    @get:Rule
    val rule: TestRule = RuleChain
        .outerRule(RegisterNavHostRegressionActivityRule())
        .around(composeRule)

    private lateinit var activity: NavHostAuditActivity
    private var capturedController: NavHostController? = null

    @Before
    fun setUp() {
        activity = composeRule.activity
        // Install the delegated auth repository before PetCareNavHost or any
        // screen can obtain a ViewModel from the AppContainer.
        activity.installAuthRepository(
            StaleSessionUserDao(activity.navUserDao())
        )
        runBlocking {
            activity.navPreferences.setUserId(null)
            activity.navPreferences.setPermissionsIntroSeen()
        }
    }

    @After
    fun tearDown() {
        composeRule.runOnIdle { activity.viewModelStore.clear() }
        repeat(3) { shadowOf(Looper.getMainLooper()).idle() }
        activity.closeNavResources()
    }

    @Test
    fun `stale session error retry then register reaches clean home`() {
        showNavHost()
        awaitDisplayed("Welcome to PetCare")
        // Inject the stale session only after Welcome is shown so this
        // deterministically exercises the protected-route guard.
        runBlocking { activity.navPreferences.setUserId(STALE_USER_ID) }
        composeRule.runOnIdle { checkNotNull(capturedController).navigate(Routes.HOME) }
        awaitDisplayed("stale-session")
        composeRule.onNodeWithText("Try again")
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        awaitDisplayed("Welcome to PetCare")
        composeRule.onNode(hasTextAndClick("Get started"))
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        awaitDisplayed("Create your account")

        composeRule.onNodeWithText("Full name *")
            .performScrollTo().assertIsDisplayed().performTextInput("Fresh Session User")
        composeRule.onNodeWithText("Email *")
            .performScrollTo().assertIsDisplayed().performTextInput("fresh-session@test.local")
        composeRule.onNodeWithText("Password *")
            .performScrollTo().assertIsDisplayed().performTextInput("secret1")
        composeRule.onNodeWithText("Confirm password *")
            .performScrollTo().assertIsDisplayed().performTextInput("secret1")
        composeRule.onNode(hasTextAndClick("Create account"))
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        awaitDisplayed(", Fresh", substring = true)
        composeRule.onNodeWithText("stale-session").assertDoesNotExist()
        composeRule.onNodeWithText("Something went wrong").assertDoesNotExist()
    }

    private fun showNavHost() {
        composeRule.setContent {
            val navController = rememberNavController()
            SideEffect {
                capturedController = navController
            }
            PetCareTheme { PetCareNavHost(startDestination = rememberStartDestination(), navController = navController) }
        }
    }

    private fun awaitDisplayed(text: String, substring: Boolean = false) {
        composeRule.waitUntil(8_000) {
            shadowOf(Looper.getMainLooper()).idle()
            runCatching {
                composeRule.onNodeWithText(text, substring = substring).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.waitForIdle()
    }

    private class StaleSessionUserDao(
        private val delegate: UserDao
    ) : UserDao {
        private var staleFindCount = 0

        override suspend fun insert(user: User): Long = delegate.insert(user)

        override suspend fun findByEmail(email: String): User? = delegate.findByEmail(email)

        override suspend fun findById(id: Long): User? =
            if (id == STALE_USER_ID) {
                staleFindCount += 1
                when (staleFindCount) {
                    1 -> error("stale-session")
                    else -> null
                }
            } else {
                delegate.findById(id)
            }

        override fun observeById(id: Long): Flow<User?> =
            if (id == STALE_USER_ID) flowOf(null) else delegate.observeById(id)

        override suspend fun updateProfile(
            id: Long,
            name: String,
            phoneNumber: String,
            photoPath: String?
        ): Int = delegate.updateProfile(id, name, phoneNumber, photoPath)

        override suspend fun updatePassword(id: Long, passwordHash: String, passwordSalt: String): Int =
            delegate.updatePassword(id, passwordHash, passwordSalt)
    }

    companion object {
        private const val STALE_USER_ID = 999L
    }
}
