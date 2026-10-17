package com.petcare.app.ui.audit

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.room.Room
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.test.onRoot
import com.petcare.app.PetCareApp
import com.petcare.app.data.AppContainer
import com.petcare.app.data.local.PetCareDatabase
import com.petcare.app.data.local.UserDao
import com.petcare.app.data.repository.AuthRepository
import com.petcare.app.navigation.PetCareNavHost
import com.petcare.app.navigation.Routes
import com.petcare.app.ui.theme.PetCareTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import androidx.test.core.app.ApplicationProvider
import org.junit.After
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
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Full native navigation coverage over the real PetCareNavHost.
 *
 * This deliberately starts at SplashScreen rather than composing individual
 * destinations. The activity supplies a real AppContainer backed by isolated
 * in-memory Room, while its Application context is the non-started PetCareApp
 * expected by SplashScreen. That keeps the test on the production navigation
 * path without starting reminder workers or using a fake navigation harness.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PetCareNavHostRobolectricTest {

    private val composeRule = createAndroidComposeRule<NavHostAuditActivity>()

    @get:Rule
    val rule: TestRule = RuleChain
        .outerRule(RegisterNavHostActivityRule())
        .around(composeRule)

    private var activity: NavHostAuditActivity? = null
    private lateinit var auth: AuthRepository
    private var capturedController: NavHostController? = null

    @Before
    fun setUp() {
        activity = composeRule.activity
        auth = activity!!.navAuth
        runBlocking { auth.logout() }
    }

    @After
    fun tearDown() {
        // ViewModels own collectors on the real Room/DataStore executors. Clear
        // them before closing the database, then drain cancellation work queued
        // on Robolectric's main looper.
        composeRule.runOnIdle { activity?.viewModelStore?.clear() }
        repeat(3) { shadowOf(Looper.getMainLooper()).idle() }
        activity?.closeNavResources()
        activity = null
    }

    @Test
    fun `authenticated native nav survives rapid tabs back stack logout and login`() {
        runBlocking {
            val user = auth.register("Nav Regression User", "nav-regression@test.local", "secret1")
                .getOrThrow()
            activity!!.navPreferences.setPermissionsIntroSeen()
        }

        composeRule.setContent { NavHostAuditContent() }
        awaitDisplayed(", Nav", substring = true)

        // Exercise each visible bottom tab with real clicks and wait for the
        // eventual destination content, not just a destination route.
        tapTab("Pets")
        awaitDisplayed("No pets yet")
        tapTab("Tasks")
        awaitDisplayed("Add a pet first")
        tapTab("Settings")
        awaitDisplayed("ACCOUNT")

        // A top-level tab is still a normal back-stack destination.
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        awaitDisplayed(", Nav", substring = true)

        // Rapid, repeated taps are intentional: every target must be visible
        // and enabled, while transitions may still be in flight.
        repeat(2) {
            listOf("Pets", "Tasks", "Settings", "Home", "Settings", "Pets", "Home")
                .forEach(::tapTab)
        }
        awaitDisplayed(", Nav", substring = true)

        // Navigate away and back to verify the restored top-level destination
        // remains usable after the rapid sequence.
        tapTab("Tasks")
        awaitDisplayed("Add a pet first")
        tapTab("Home")
        awaitDisplayed(", Nav", substring = true)
        tapTab("Tasks")
        awaitDisplayed("Add a pet first")

        // Log out through the visible settings control and confirmation dialog.
        tapTab("Settings")
        awaitDisplayed("ACCOUNT")
        composeRule.onNodeWithText("Log out").performScrollTo().assertIsDisplayed().performClick()
        awaitDisplayed("Log out?")
        val logoutButtons = composeRule.onAllNodesWithText("Log out")
        logoutButtons[logoutButtons.fetchSemanticsNodes().lastIndex]
            .assertIsDisplayed()
            .performClick()
        awaitDisplayed("Welcome to PetCare")

        // Log back in through the real form, then prove authenticated nav is
        // restored rather than merely showing an auth destination.
        composeRule.onNode(hasTextAndClick("Log in"))
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        awaitDisplayed("Email *")
        composeRule.onNodeWithText("Email *")
            .performScrollTo()
            .assertIsDisplayed()
            .performTextInput("nav-regression@test.local")
        composeRule.onNodeWithText("Password *")
            .performScrollTo()
            .assertIsDisplayed()
            .performTextInput("secret1")
        composeRule.onNode(hasTextAndClick("Log in"))
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        awaitDisplayed(", Nav", substring = true)
        tapTab("Pets")
        awaitDisplayed("No pets yet")
        tapTab("Home")
        awaitDisplayed(", Nav", substring = true)
    }

    private fun tapTab(label: String) {
        composeRule.onNode(hasTextAndClick(label))
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
    }

    private fun awaitDisplayed(text: String, substring: Boolean = false) {
        try {
            composeRule.waitUntil(8_000) {
                shadowOf(Looper.getMainLooper()).idle()
                runCatching {
                    composeRule.onNodeWithText(text, substring = substring).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
        } catch (failure: Throwable) {
            throw AssertionError(navDiagnostic("awaitDisplayed", text), failure)
        }
        // Visible incoming content can still be covered by an outgoing
        // NavHost transition. Complete its virtual frames before the next tap.
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.waitForIdle()
    }

    private fun currentTopLevelRoute(): String? =
        Routes.canonicalTopLevel(capturedController?.currentDestination?.route)

    @Composable
    private fun NavHostAuditContent() {
        val navController = rememberNavController()
        SideEffect { capturedController = navController }
        PetCareTheme { PetCareNavHost(startDestination = rememberStartDestination(), navController = navController) }
    }

    private fun navDiagnostic(operation: String, expected: String): String {
        val route = capturedController?.currentDestination?.route
        val stack = capturedController?.currentBackStack?.value
            ?.joinToString(" > ") { it.destination.route ?: "<graph>" }
            ?: "<not captured>"
        val session = runCatching { runBlocking { auth.signedInUserId() } }
            .getOrElse { "<session error: ${it::class.simpleName}: ${it.message}>" }
        val semantics = runCatching {
            composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode().config.toString()
        }.getOrElse { "<semantics unavailable: ${it::class.simpleName}>" }
        return "Navigation diagnostic: $operation expected='$expected' " +
            "route=$route stack=$stack session=$session " +
            "lifecycle=${activity?.lifecycle?.currentState} rootSemantics=$semantics"
    }
}

private fun hasTextAndClick(text: String) = hasClickAction().and(hasText(text))

/**
 * Component registration is needed because this test activity intentionally
 * exists only in the JVM test source set and is not in the production manifest.
 */
private class RegisterNavHostActivityRule : TestRule {
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

/**
 * Isolated real application graph for Robolectric. This mirrors the existing
 * audit activity's dependency wiring but returns PetCareApp from
 * getApplicationContext(), which the app's ViewModel factory requires.
 */
class NavHostAuditActivity : ComponentActivity() {
    val navAuth: AuthRepository
        get() = navContainer.authRepository
    val navPreferences: com.petcare.app.data.preferences.UserPreferences
        get() = navContainer.preferences

    private lateinit var navApplication: PetCareApp
    private lateinit var navDatabase: PetCareDatabase
    private lateinit var navContainer: AppContainer
    private var preferenceRoot: File? = null
    private var closed = false

    override val defaultViewModelCreationExtras: CreationExtras
        get() = MutableCreationExtras(super.defaultViewModelCreationExtras).apply {
            this[APPLICATION_KEY] = navApplication
            this[DEFAULT_ARGS_KEY] = Bundle()
        }

    override fun getApplicationContext(): Context = navApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        // Room asks the activity for its application context while building.
        // Initialize the synthetic PetCareApp before that call; otherwise the
        // overridden getApplicationContext() reads an uninitialised lateinit.
        navApplication = PetCareApp()
        try {
            // A manually constructed Application has no ContextWrapper base.
            // Since the activity exposes it as its application context,
            // PhoneWindow can otherwise reach ContextWrapper.getTheme() with
            // a null mBase. Attach it to Robolectric's real application before
            // Android or Room asks the synthetic application for resources.
            attachNavApplicationBase(
                navApplication,
                ApplicationProvider.getApplicationContext<Application>()
            )
            navDatabase = Room.inMemoryDatabaseBuilder(this, PetCareDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            val root = File(
                cacheDir,
                "petcare-nav-regression-preferences-${nextNavPreferenceDirectory.incrementAndGet()}"
            ).apply { mkdirs() }
            preferenceRoot = root
            setNavDatabaseInstance(navDatabase)
            resetNavPreferenceDelegate()
            val container = AppContainer(NavPreferencesContext(this, root))
            PetCareApp::class.java.getDeclaredField("container").apply {
                isAccessible = true
                set(navApplication, container)
            }
            navContainer = container
            super.onCreate(savedInstanceState)
        } catch (error: Throwable) {
            closeNavResources()
            throw error
        }
    }

    override fun onDestroy() {
        closeNavResources()
        super.onDestroy()
    }

    /**
     * Replaces the container's lazy auth repository before any screen creates
     * a ViewModel. The delegated DAO is intentionally supplied by a regression
     * test so stale-session failures can be reproduced without production
     * hooks or a second database.
     */
    fun installAuthRepository(userDao: UserDao): AuthRepository {
        val replacement = AuthRepository(userDao, navContainer.preferences)
        AppContainer::class.java.getDeclaredField("authRepository\$delegate").apply {
            isAccessible = true
            set(navContainer, lazyOf(replacement))
        }
        return replacement
    }

    fun navUserDao(): UserDao = navDatabase.userDao()

    fun closeNavResources() {
        if (closed) return
        closed = true
        runCatching { if (::navDatabase.isInitialized) navDatabase.close() }
        runCatching { setNavDatabaseInstance(null) }
        runCatching { preferenceRoot?.deleteRecursively() }
        runCatching { resetNavPreferenceDelegate() }
    }
}

private class NavPreferencesContext(
    base: Context,
    private val filesRoot: File
) : ContextWrapper(base) {
    override fun getApplicationContext(): Context = this
    override fun getFilesDir(): File = filesRoot
}

private fun resetNavPreferenceDelegate() {
    val holder = Class.forName("com.petcare.app.data.preferences.UserPreferencesKt")
    val delegateField = holder.declaredFields.firstOrNull {
        it.name == "dataStore" || it.name == "dataStore\$delegate"
    } ?: error("UserPreferences DataStore delegate field was not found")
    val delegate = delegateField.apply { isAccessible = true }.get(null)
    delegate.javaClass.getDeclaredField("INSTANCE").apply {
        isAccessible = true
        set(delegate, null)
    }
}

private fun setNavDatabaseInstance(database: PetCareDatabase?) {
    PetCareDatabase::class.java.getDeclaredField("instance").apply {
        isAccessible = true
        set(null, database)
    }
}

private fun attachNavApplicationBase(application: PetCareApp, base: Context) {
    ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java).apply {
        isAccessible = true
        invoke(application, base)
    }
}

private val nextNavPreferenceDirectory = AtomicLong()

/**
 * Mirrors MainActivity: the session is restored before the NavHost exists
 * (behind the system splash in production), then the start route is fixed.
 */
@androidx.compose.runtime.Composable
internal fun rememberStartDestination(): String {
    val activity = androidx.compose.ui.platform.LocalContext.current as NavHostAuditActivity
    return androidx.compose.runtime.remember {
        runBlocking { if (activity.navAuth.restoreSession() != null) Routes.HOME else Routes.WELCOME }
    }
}
