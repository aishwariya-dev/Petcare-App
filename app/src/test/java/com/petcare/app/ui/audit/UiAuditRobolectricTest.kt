package com.petcare.app.ui.audit

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.petcare.app.PetCareApp
import com.petcare.app.data.AppContainer
import com.petcare.app.data.local.PetCareDatabase
import com.petcare.app.data.repository.AuthRepository
import com.petcare.app.data.repository.ExpenseRepository
import com.petcare.app.data.repository.PetRepository
import com.petcare.app.data.repository.TaskRepository
import com.petcare.app.model.CareTask
import com.petcare.app.model.Frequency
import com.petcare.app.model.Pet
import com.petcare.app.model.Species
import com.petcare.app.model.TaskCategory
import com.petcare.app.model.ThemeMode
import com.petcare.app.navigation.Routes
import com.petcare.app.ui.components.ConfirmationDialog
import com.petcare.app.ui.components.FormTextField
import com.petcare.app.ui.common.FeedbackController
import com.petcare.app.ui.common.LocalFeedback
import com.petcare.app.ui.screens.auth.LoginScreen
import com.petcare.app.ui.screens.auth.RegisterScreen
import com.petcare.app.ui.screens.expenses.ExpenseFormScreen
import com.petcare.app.ui.screens.settings.AboutScreen
import com.petcare.app.ui.screens.tasks.TaskFormViewModel
import com.petcare.app.ui.screens.tasks.TaskFormScreen
import com.petcare.app.ui.screens.tasks.TaskDetailScreen
import com.petcare.app.ui.theme.PetCareTheme
import com.petcare.app.util.ImageStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong

/**
 * Bounded Compose UI checks for the native form controls.
 *
 * The production screens obtain their ViewModels from the application service
 * locator. AuditActivity supplies a fake PetCareApp only as a ViewModel
 * creation extra; it never calls PetCareApp.onCreate, so reminders and
 * navigation are not started. Its AppContainer still uses the real
 * repositories over a Room in-memory database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class UiAuditRobolectricTest {

    private val composeRule = createAndroidComposeRule<AuditActivity>()

    /**
     * The registration rule must be outside ActivityScenario's rule. JUnit
     * applies this chain before the compose rule attempts its launcher intent.
     */
    @get:Rule
    val rule: TestRule = RuleChain
        .outerRule(RegisterAuditActivityRule())
        .around(composeRule)

    private var context: AuditActivity? = null
    private lateinit var database: PetCareDatabase
    private lateinit var auth: AuthRepository
    private lateinit var expenseRepository: ExpenseRepository
    private lateinit var petRepository: PetRepository
    private lateinit var taskRepository: TaskRepository
    private lateinit var imageStorage: ImageStorage
    private var editProbeViewModel: TaskFormViewModel? = null

    @Before
    fun setUp() {
        val activity = composeRule.activity
        context = activity
        database = activity.auditDatabase
        auth = activity.auditAuth
        imageStorage = activity.auditImageStorage
        expenseRepository = activity.auditExpenseRepository
        petRepository = activity.auditPetRepository
        taskRepository = activity.auditTaskRepository
        runBlocking { auth.logout() }
    }

    @After
    fun tearDown() {
        composeRule.runOnIdle {
            editProbeViewModel?.viewModelScope?.cancel()
            context?.viewModelStore?.clear()
        }
        // Drain cancellation continuations before closing Room. Blocking logout
        // here can wait for a session mutex held by work queued on this looper.
        shadowOf(Looper.getMainLooper()).idle()
        context?.closeAuditResources()
        context = null
    }

    @Test
    fun `about privacy distinguishes delegation handoff from automatic sms`() {
        composeRule.setContent {
            PetCareTheme(themeMode = ThemeMode.LIGHT) {
                AboutScreen(onBack = {})
            }
        }

        composeRule.onNodeWithText(
            "Care delegation opens your messaging app with text for you to review and send.",
            substring = true
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            "Automatic task SMS is separate, off by default, and can submit a scheduled message",
            substring = true
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            "Messages are sent only from your own messaging app",
            substring = true
        ).assertDoesNotExist()
    }

    @Test
    fun `login validation renders and remains usable after repeated invalid submit`() {
        composeRule.setContent {
            AuditFeedbackProvider {
                PetCareTheme(themeMode = ThemeMode.LIGHT) {
                    LoginScreen(onBack = {}, onLoggedIn = {}, onRegister = {})
                }
            }
        }

        val submit = composeRule.onNode(hasTextAndClick("Log in"))
        submit.performScrollTo().assertIsDisplayed().assertIsEnabled()
        submit.performTouchInput { click() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Email is required").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Password is required").performScrollTo().assertIsDisplayed()
        submit.assertIsEnabled()

        // The form must not strand the user after an identical second attempt.
        submit.performScrollTo().assertIsDisplayed()
        submit.performTouchInput { click() }
        composeRule.waitForIdle()
        submit.assertIsEnabled()
    }

    @Test
    fun `register validation renders and remains usable after repeated invalid submit`() {
        composeRule.setContent {
            AuditFeedbackProvider {
                PetCareTheme(themeMode = ThemeMode.LIGHT) {
                    RegisterScreen(onBack = {}, onRegistered = {}, onLogin = {})
                }
            }
        }

        val submit = composeRule.onNode(hasTextAndClick("Create account"))
        submit.performScrollTo().assertIsDisplayed().assertIsEnabled()
        submit.performTouchInput { click() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Name is required").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Email is required").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Password is required").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Please confirm your password")
            .performScrollTo()
            .assertIsDisplayed()
        submit.assertIsEnabled()

        submit.performScrollTo().assertIsDisplayed()
        submit.performTouchInput { click() }
        composeRule.waitForIdle()
        submit.assertIsEnabled()
    }

    @Test
    fun `expense validation renders and remains usable after repeated invalid submit`() {
        composeRule.setContent {
            AuditFeedbackProvider {
                PetCareTheme(themeMode = ThemeMode.LIGHT) {
                    ExpenseFormScreen(onBack = {}, onSaved = {})
                }
            }
        }

        val submit = composeRule.onNode(hasTextAndClick("Save expense"))
        submit.performScrollTo().assertIsDisplayed().assertIsEnabled()
        submit.performTouchInput { click() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Amount is required").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Description is required").performScrollTo().assertIsDisplayed()
        submit.assertIsEnabled()

        submit.performScrollTo().assertIsDisplayed()
        submit.performTouchInput { click() }
        composeRule.waitForIdle()
        submit.assertIsEnabled()
    }

    @Test
    fun `expense create controls stay create mode while entering amount and description`() {
        composeRule.setContent {
            AuditFeedbackProvider {
                PetCareTheme(themeMode = ThemeMode.LIGHT) {
                    ExpenseFormScreen(onBack = {}, onSaved = {})
                }
            }
        }

        composeRule.onNodeWithText("Add expense").assertIsDisplayed()
        composeRule.onNodeWithText("Amount (£) *").performScrollTo().assertIsDisplayed().performTextInput("12.50")
        composeRule.onNodeWithText("Description *").performScrollTo().assertIsDisplayed().performTextInput("Vet check-up")
        composeRule.waitForIdle()

        // Typed values are still a create form, not an edit-route transition.
        composeRule.onNodeWithText("Add expense").assertIsDisplayed()
        composeRule.onNodeWithText("Edit expense").assertDoesNotExist()
    }

    @Test
    fun `expense fields save through the real repository`() {
        val user = registerUser("expense-ui@test.local")
        var saved = false
        composeRule.setContent {
            AuditFeedbackProvider {
                PetCareTheme(themeMode = ThemeMode.LIGHT) {
                    ExpenseFormScreen(onBack = {}, onSaved = { saved = true })
                }
            }
        }

        composeRule.onNodeWithText("Amount (£) *").performScrollTo().assertIsDisplayed().performTextInput("12.50")
        composeRule.onNodeWithText("Description *").performScrollTo().assertIsDisplayed().performTextInput("Vet check-up")
        val submit = composeRule.onNode(hasTextAndClick("Save expense"))
        submit.performScrollTo().assertIsDisplayed().assertIsEnabled()
        submit.performTouchInput { click() }

        awaitUi { saved }
        val expenses = runBlocking {
            database.expenseDao().observeForOwner(user.id).first()
        }
        assertEquals(1, expenses.size)
        assertEquals(12.50, expenses.single().amount, 0.0)
        assertEquals("Vet check-up", expenses.single().description)
    }

    @Test
    fun `task create controls render and repeated missing-time submit stays usable`() {
        val user = registerUser("task-ui@test.local")
        val pet = Pet(ownerId = user.id, name = "Milo", species = Species.DOG.name)
        val petId = runBlocking { database.petDao().insert(pet) }
        composeRule.setContent {
            AuditFeedbackProvider {
                PetCareTheme(themeMode = ThemeMode.LIGHT) {
                    TaskFormScreen(onBack = {}, onSaved = {}, onAddPet = {})
                }
            }
        }

        awaitUi {
            composeRule.onAllNodesWithText("Create task").fetchSemanticsNodes().isNotEmpty()
        }
        val submit = composeRule.onNode(hasTextAndClick("Create task"))
        submit.performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Task name *").performScrollTo().assertIsDisplayed().performTextInput("Morning walk")
        submit.performScrollTo().assertIsDisplayed()
        submit.assertIsEnabled().performTouchInput { click() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Choose a time").performScrollTo().assertIsDisplayed()
        submit.assertIsEnabled()
        submit.performScrollTo().assertIsDisplayed()
        submit.assertIsEnabled().performTouchInput { click() }
        composeRule.waitForIdle()
        submit.assertIsEnabled()
    }

    @Test
    fun `task edit ViewModel hydrates real data into editable controls`() {
        val user = registerUser("task-edit-ui@test.local")
        val petId = runBlocking {
            database.petDao().insert(Pet(ownerId = user.id, name = "Luna", species = Species.CAT.name))
        }
        val taskId = runBlocking {
            database.careTaskDao().insert(
                CareTask(
                    petId = petId,
                    name = "Brush coat",
                    category = TaskCategory.GROOMING.name,
                    frequency = Frequency.WEEKLY.name,
                    startDateEpochDay = LocalDate.now().toEpochDay(),
                    timeMinutes = 8 * 60
                )
            )
        }
        val viewModel = composeRule.runOnIdle { TaskFormViewModel(
            auth,
            petRepository,
            taskRepository,
            imageStorage,
            SavedStateHandle(
                mapOf(
                    Routes.ARG_TASK_ID to taskId,
                    Routes.ARG_PET_ID to -1L
                )
            )
        ).also { editProbeViewModel = it } }

        composeRule.setContent {
            AuditFeedbackProvider {
                PetCareTheme(themeMode = ThemeMode.LIGHT) {
                    TaskEditProbe(viewModel)
                }
            }
        }
        awaitUi { !viewModel.state.value.isLoading }

        composeRule.onNodeWithText("Edit task").assertIsDisplayed()
        composeRule.onNodeWithText("Brush coat").assertIsDisplayed()
        composeRule.onNodeWithText("Save changes").assertIsDisplayed()
    }

    @Test
    fun `future task detail visibly disables early completion`() {
        val id = prepareDetailTask(LocalDate.now().plusDays(1))
        showTaskDetail()
        awaitUi {
            composeRule.onAllNodesWithText("Mark as done").fetchSemanticsNodes().isNotEmpty()
        }
        val action = composeRule.onNode(hasTextAndClick("Mark as done"))
        action.performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        action.performTouchInput { click() }
        composeRule.waitForIdle()
        assertNull(runBlocking { taskRepository.getTask(id) }!!.lastCompletedEpochDay)
    }

    @Test
    fun `overdue task detail permits visible completion and undo taps`() {
        val id = prepareDetailTask(LocalDate.now().minusDays(2))
        showTaskDetail()
        awaitUi {
            composeRule.onAllNodesWithText("Mark as done").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasTextAndClick("Mark as done"))
            .performScrollTo().assertIsDisplayed().assertIsEnabled().performTouchInput { click() }
        awaitUi {
            composeRule.onAllNodesWithText("Mark as not done").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(LocalDate.now().toEpochDay(), runBlocking { taskRepository.getTask(id) }!!.lastCompletedEpochDay)
        composeRule.onNode(hasTextAndClick("Mark as not done"))
            .performScrollTo().assertIsDisplayed().assertIsEnabled().performTouchInput { click() }
        awaitUi {
            composeRule.onAllNodesWithText("Mark as done").fetchSemanticsNodes().isNotEmpty()
        }
        assertNull(runBlocking { taskRepository.getTask(id) }!!.lastCompletedEpochDay)
    }

    private fun prepareDetailTask(date: LocalDate): Long {
        val user = registerUser("detail-ui@test.local")
        val id = runBlocking {
            val petId = database.petDao().insert(Pet(ownerId = user.id, name = "Milo", species = Species.DOG.name))
            taskRepository.save(CareTask(
                petId = petId, name = "Vet visit", category = TaskCategory.HEALTH_CHECK.name,
                frequency = Frequency.ONCE.name, startDateEpochDay = date.toEpochDay(), timeMinutes = 9 * 60
            ))
        }
        composeRule.runOnIdle {
            composeRule.activity.auditArguments = Bundle().apply { putLong(Routes.ARG_TASK_ID, id) }
        }
        return id
    }

    private fun showTaskDetail() = composeRule.setContent {
        AuditFeedbackProvider {
            PetCareTheme(themeMode = ThemeMode.LIGHT) {
                TaskDetailScreen(onBack = {}, onEdit = {}, onDeleted = {}, onOpenPet = {}, onDelegate = {})
            }
        }
    }

    @Test
    fun `confirmation dialog cancel and confirm invoke only their own callbacks`() {
        var dismissed = 0
        var confirmed = 0
        var visible by mutableStateOf(true)

        composeRule.setContent {
            PetCareTheme(themeMode = ThemeMode.LIGHT) {
                if (visible) {
                    ConfirmationDialog(
                        title = "Delete task?",
                        message = "This cannot be undone.",
                        confirmLabel = "Delete",
                        onConfirm = {
                            confirmed += 1
                            visible = false
                        },
                        onDismiss = {
                            dismissed += 1
                            visible = false
                        }
                    )
                }
            }
        }

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()
        assertEquals(1, dismissed)
        assertEquals(0, confirmed)
        composeRule.onNodeWithText("Delete task?").assertDoesNotExist()

        visible = true
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.waitForIdle()
        assertEquals(1, dismissed)
        assertEquals(1, confirmed)
        composeRule.onNodeWithText("Delete task?").assertDoesNotExist()
    }

    @Test
    fun `palette selection updates the MaterialTheme color scheme`() {
        var observedMode: ThemeMode? = null
        var observedBackground = Color.Unspecified

        composeRule.setContent {
            var mode by remember { mutableStateOf(ThemeMode.LIGHT) }
            PetCareTheme(themeMode = mode) {
                val background = MaterialTheme.colorScheme.background
                SideEffect {
                    observedMode = mode
                    observedBackground = background
                }
                Button(onClick = {
                    mode = if (mode == ThemeMode.LIGHT) ThemeMode.DARK else ThemeMode.LIGHT
                }) {
                    Text("Toggle theme")
                }
            }
        }

        composeRule.waitForIdle()
        assertEquals(ThemeMode.LIGHT, observedMode)
        val light = observedBackground
        composeRule.onNodeWithText("Toggle theme").performClick()
        composeRule.waitForIdle()
        assertEquals(ThemeMode.DARK, observedMode)
        val dark = observedBackground

        assertNotEquals(light, dark)
        assertNotEquals(Color.Unspecified, light)
        assertNotEquals(Color.Unspecified, dark)
    }

    private fun registerUser(email: String) = runBlocking {
        auth.register("UI Audit User", email, "secret1").getOrThrow()
    }

    private fun awaitUi(predicate: () -> Boolean) {
        composeRule.waitUntil(5_000) {
            // Compose's frame clock does not advance Android Handler work
            // queued by Room/ViewModel continuations on Robolectric's looper.
            shadowOf(Looper.getMainLooper()).idle()
            predicate()
        }
    }

    @Composable
    private fun TaskEditProbe(viewModel: TaskFormViewModel) {
        val state by viewModel.state.collectAsStateWithLifecycle()
        Column {
            Text(if (state.isEdit) "Edit task" else "Create care task")
            FormTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = "Task name",
                required = true,
                error = state.nameError
            )
            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.isEdit) "Save changes" else "Create task")
            }
        }
    }
}

@Composable
private fun AuditFeedbackProvider(content: @Composable () -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val feedback = remember { FeedbackController(snackbarHostState, scope) }
    CompositionLocalProvider(LocalFeedback provides feedback, content = content)
}

private fun hasTextAndClick(text: String) = hasClickAction().and(hasText(text))

/**
 * The real app application starts reminder coordination in onCreate. This
 * activity provides the same application container as a ViewModel creation
 * extra, but uses a non-started PetCareApp instance so UI tests remain
 * bounded. All container repositories still point at the generated Room
 * implementation over an in-memory database.
 */
class AuditActivity : ComponentActivity() {
    var auditArguments: Bundle? = null
    lateinit var auditDatabase: PetCareDatabase
        private set
    lateinit var auditAuth: AuthRepository
        private set
    lateinit var auditExpenseRepository: ExpenseRepository
        private set
    lateinit var auditPetRepository: PetRepository
        private set
    lateinit var auditTaskRepository: TaskRepository
        private set
    lateinit var auditImageStorage: ImageStorage
        private set

    private lateinit var auditApplication: PetCareApp
    private var auditPreferenceRoot: File? = null
    private var auditClosed = false

    override val defaultViewModelCreationExtras: CreationExtras
        get() = MutableCreationExtras(super.defaultViewModelCreationExtras).apply {
            this[APPLICATION_KEY] = auditApplication
            auditArguments?.let { this[DEFAULT_ARGS_KEY] = it }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        val database = Room.inMemoryDatabaseBuilder(this, PetCareDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        auditDatabase = database
        val preferenceRoot = File(
            cacheDir,
            "petcare-ui-audit-preferences-${nextPreferenceDirectory.incrementAndGet()}"
        ).apply { mkdirs() }
        auditPreferenceRoot = preferenceRoot

        try {
            setDatabaseInstance(database)
            resetProductionPreferenceDelegate()

            auditApplication = PetCareApp()
            val preferenceContext = AuditPreferencesContext(this, preferenceRoot)
            val container = AppContainer(preferenceContext)
            PetCareApp::class.java.getDeclaredField("container").apply {
                isAccessible = true
                set(auditApplication, container)
            }
            auditAuth = container.authRepository
            auditExpenseRepository = container.expenseRepository
            auditPetRepository = container.petRepository
            auditTaskRepository = container.taskRepository
            auditImageStorage = container.imageStorage

            super.onCreate(savedInstanceState)
        } catch (error: Throwable) {
            closeAuditResources()
            throw error
        }
    }

    override fun onDestroy() {
        closeAuditResources()
        super.onDestroy()
    }

    fun closeAuditResources() {
        if (auditClosed) return
        auditClosed = true
        runCatching {
            if (::auditDatabase.isInitialized) auditDatabase.close()
        }
        runCatching { setDatabaseInstance(null) }
        runCatching { auditPreferenceRoot?.deleteRecursively() }
        runCatching { resetProductionPreferenceDelegate() }
    }
}

private class AuditPreferencesContext(
    base: Context,
    private val filesRoot: File
) : ContextWrapper(base) {
    override fun getApplicationContext(): Context = this
    override fun getFilesDir(): File = filesRoot
}

/**
 * This follows the working data/TestSupport reset: UserPreferences owns a
 * process-level DataStore delegate, so each UI activity gets a unique files
 * root and the delegate is reset before and after setup.
 */
private fun resetProductionPreferenceDelegate() {
    val holder = Class.forName("com.petcare.app.data.preferences.UserPreferencesKt")
    val delegateField = holder.declaredFields.firstOrNull {
        it.name == "dataStore" || it.name == "dataStore\$delegate"
    } ?: error("UserPreferences DataStore delegate field was not found")
    val delegate = delegateField.apply {
        isAccessible = true
    }.get(null)
    delegate.javaClass.getDeclaredField("INSTANCE").apply {
        isAccessible = true
        set(delegate, null)
    }
}

private fun setDatabaseInstance(database: PetCareDatabase?) {
    // The singleton field is compiled onto PetCareDatabase itself, not onto
    // its Companion. Keep the inspected target explicit.
    PetCareDatabase::class.java.getDeclaredField("instance").apply {
        isAccessible = true
        set(null, database)
    }
}

private class RegisterAuditActivityRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                val context = ApplicationProvider.getApplicationContext<Application>()
                val packageManager = shadowOf(context.packageManager)
                val component = ComponentName(context, AuditActivity::class.java)
                packageManager.addActivityIfNotPresent(component)
                try {
                    base.evaluate()
                } finally {
                    // A failed ActivityScenario setup must not leave this
                    // synthetic activity registered for another Robolectric
                    // test in the same sandbox.
                    packageManager.removeActivity(component)
                }
            }
        }
}

private val nextPreferenceDirectory = AtomicLong()