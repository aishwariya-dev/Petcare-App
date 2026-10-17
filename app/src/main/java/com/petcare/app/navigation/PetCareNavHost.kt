package com.petcare.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.petcare.app.ui.common.FeedbackController
import com.petcare.app.ui.common.LocalFeedback
import com.petcare.app.ui.screens.auth.LoginScreen
import com.petcare.app.ui.screens.auth.RegisterScreen
import com.petcare.app.ui.screens.auth.WelcomeScreen
import com.petcare.app.ui.screens.dashboard.DashboardScreen
import com.petcare.app.ui.screens.delegation.DelegateScreen
import com.petcare.app.ui.screens.delegation.SmsPreviewScreen
import com.petcare.app.ui.screens.expenses.ExpenseFormScreen
import com.petcare.app.ui.screens.expenses.ExpensesScreen
import com.petcare.app.ui.screens.pets.PetFormScreen
import com.petcare.app.ui.screens.pets.PetProfileScreen
import com.petcare.app.ui.screens.pets.PetsScreen
import com.petcare.app.ui.screens.settings.AboutScreen
import com.petcare.app.ui.screens.settings.ProfileScreen
import com.petcare.app.ui.screens.settings.SettingsScreen
import com.petcare.app.ui.screens.permissions.PermissionsScreen
import com.petcare.app.ui.screens.nearby.NearbyCareScreen
import com.petcare.app.ui.common.rememberAppContainer
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.first
import com.petcare.app.ui.screens.tasks.TaskDetailScreen
import com.petcare.app.ui.screens.tasks.TaskFormScreen
import com.petcare.app.ui.screens.tasks.TasksScreen
import com.petcare.app.ui.screens.upcoming.UpcomingScreen

private data class BottomTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector
)

private val bottomTabs = listOf(
    BottomTab(Routes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    BottomTab(Routes.PETS, "Pets", Icons.Filled.Pets, Icons.Outlined.Pets),
    BottomTab(Routes.TASKS, "Tasks", Icons.Filled.Checklist, Icons.Outlined.Checklist),
    BottomTab(Routes.SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
)

/** Material fade-through: the old tab fades out, the new one fades and grows in. */
private const val FADE_THROUGH_OUT_MILLIS = 90
private const val FADE_THROUGH_IN_MILLIS = 210
private const val FADE_THROUGH_SCALE = 0.92f

/** Shared-axis push/pop for detail and form screens. */
private const val PUSH_MILLIS = 300
private const val PUSH_OFFSET_FRACTION = 0.3f
private const val PUSH_PARALLAX_FRACTION = 0.1f

/**
 * The signed-in shell plus the auth graph.
 *
 * @param startDestination [Routes.HOME] when a session was restored, otherwise [Routes.WELCOME].
 * The system splash covers the restore, so no in-app splash route exists.
 */
@Composable
fun PetCareNavHost(
    startDestination: String,
    navController: NavHostController = rememberNavController()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val feedback = remember { FeedbackController(snackbarHostState, scope) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentTopLevel = Routes.canonicalTopLevel(currentRoute)
    val showBottomBar = currentTopLevel != null && currentTopLevel in Routes.topLevel

    CompositionLocalProvider(LocalFeedback provides feedback) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        bottomTabs.forEach { tab ->
                            val selected = currentTopLevel == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navController.navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        if (selected) tab.selectedIcon else tab.icon,
                                        contentDescription = null
                                    )
                                },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(Modifier.padding(innerPadding)) {
                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    enterTransition = {
                        if (isTopLevelChange()) fadeThroughIn() else pushIn(AnimatedContentTransitionScope.SlideDirection.Start)
                    },
                    exitTransition = {
                        if (isTopLevelChange()) fadeThroughOut() else pushOut(AnimatedContentTransitionScope.SlideDirection.Start)
                    },
                    popEnterTransition = {
                        if (isTopLevelChange()) fadeThroughIn() else pushIn(AnimatedContentTransitionScope.SlideDirection.End)
                    },
                    popExitTransition = {
                        if (isTopLevelChange()) fadeThroughOut() else pushOut(AnimatedContentTransitionScope.SlideDirection.End)
                    }
                ) {
                    authGraph(navController)
                    mainGraph(navController)
                }
                SessionGuard(navController, currentRoute)
            }
        }
    }
}

private fun fadeThroughIn() =
    fadeIn(tween(FADE_THROUGH_IN_MILLIS, delayMillis = FADE_THROUGH_OUT_MILLIS)) +
        scaleIn(
            tween(FADE_THROUGH_IN_MILLIS, delayMillis = FADE_THROUGH_OUT_MILLIS),
            initialScale = FADE_THROUGH_SCALE
        )

private fun fadeThroughOut() = fadeOut(tween(FADE_THROUGH_OUT_MILLIS))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.pushIn(
    direction: AnimatedContentTransitionScope.SlideDirection
) = slideIntoContainer(
    direction,
    tween(PUSH_MILLIS, easing = FastOutSlowInEasing),
    initialOffset = { fullSize -> (fullSize * PUSH_OFFSET_FRACTION).toInt() }
) + fadeIn(tween(PUSH_MILLIS))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.pushOut(
    direction: AnimatedContentTransitionScope.SlideDirection
) = slideOutOfContainer(
    direction,
    tween(PUSH_MILLIS, easing = FastOutSlowInEasing),
    targetOffset = { fullSize -> (fullSize * PUSH_PARALLAX_FRACTION).toInt() }
) + fadeOut(tween(PUSH_MILLIS))

private fun NavHostController.navigateToTab(route: String) {
    if (Routes.canonicalTopLevel(currentDestination?.route) == route) return
    val target = if (route == Routes.TASKS) Routes.tasks() else route
    navigate(target) {
        // Home remains the authenticated anchor.
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTopLevelChange(): Boolean {
    val initial = Routes.canonicalTopLevel(initialState.destination.route)
    val target = Routes.canonicalTopLevel(targetState.destination.route)
    return initial != null && target != null
}

/** Clears the back stack and lands on the given destination (used after login/logout). */
fun NavHostController.resetTo(route: String) {
    // Saved tab stacks hold ViewModels too; never restore another session's UI.
    listOf(Routes.HOME, Routes.PETS, Routes.TASKS_WITH_TAB, Routes.SETTINGS).forEach {
        clearBackStack(it)
    }
    navigate(route) {
        popUpTo(graph.id) { inclusive = false }
        launchSingleTop = true
    }
}

private fun androidx.navigation.NavGraphBuilder.authGraph(navController: NavHostController) {
    composable(Routes.WELCOME) {
        WelcomeScreen(
            onGetStarted = { navController.navigate(Routes.REGISTER) },
            onLogin = { navController.navigate(Routes.LOGIN) }
        )
    }
    composable(Routes.LOGIN) {
        LoginScreen(
            onBack = { navController.popBackStack() },
            onLoggedIn = { navController.resetTo(Routes.HOME) },
            onRegister = {
                navController.navigate(Routes.REGISTER) {
                    popUpTo(Routes.WELCOME); launchSingleTop = true
                }
            }
        )
    }
    composable(Routes.REGISTER) {
        RegisterScreen(
            onBack = { navController.popBackStack() },
            onRegistered = { navController.resetTo(Routes.HOME) },
            onLogin = {
                navController.navigate(Routes.LOGIN) {
                    popUpTo(Routes.WELCOME); launchSingleTop = true
                }
            }
        )
    }
}


private fun androidx.navigation.NavGraphBuilder.mainGraph(navController: NavHostController) {
    composable(Routes.HOME) {
        // First arrival in the signed-in app on this phone: show the
        // permissions page once, on top of Home, so Back always lands here.
        val preferences = rememberAppContainer().preferences
        LaunchedEffect(Unit) {
            if (!preferences.permissionsIntroSeen.first()) navController.navigate(Routes.PERMISSIONS)
        }
        DashboardScreen(
            onOpenTask = { navController.navigate(Routes.taskDetail(it)) },
            onOpenPet = { navController.navigate(Routes.petDetail(it)) },
            onCreateTask = { navController.navigate(Routes.addTask()) },
            onAddPet = { navController.navigate(Routes.addPet()) },
            onOpenUpcoming = { navController.navigate(Routes.UPCOMING) },
            onOpenExpenses = { navController.navigate(Routes.EXPENSES) },
            onOpenNearbyCare = { navController.navigate(Routes.NEARBY_CARE) },
            onOpenProfile = { navController.navigate(Routes.PROFILE) }
        )
    }
    composable(Routes.PETS) {
        PetsScreen(
            onOpenPet = { navController.navigate(Routes.petDetail(it)) },
            onAddPet = { navController.navigate(Routes.addPet()) }
        )
    }
    composable(
        route = Routes.TASKS_WITH_TAB,
        arguments = listOf(
            navArgument(Routes.ARG_TAB) {
                type = NavType.StringType
                defaultValue = Routes.DEFAULT_TASK_TAB
            }
        )
    ) {
        TasksScreen(
            onOpenTask = { navController.navigate(Routes.taskDetail(it)) },
            onCreateTask = { startEpochDay -> navController.navigate(Routes.addTask(startEpochDay = startEpochDay)) },
            onAddPet = { navController.navigate(Routes.addPet()) }
        )
    }
    composable(Routes.SETTINGS) {
        SettingsScreen(
            onOpenProfile = { navController.navigate(Routes.PROFILE) },
            onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
            onOpenAbout = { navController.navigate(Routes.ABOUT) },
            onLoggedOut = { navController.resetTo(Routes.WELCOME) }
        )
    }
    composable(Routes.PERMISSIONS) {
        PermissionsScreen(onDone = { navController.popBackStack() })
    }
    composable(Routes.NEARBY_CARE) {
        NearbyCareScreen(onBack = { navController.popBackStack() })
    }
    composable(Routes.UPCOMING) {
        UpcomingScreen(
            onBack = { navController.popBackStack() },
            onOpenTask = { navController.navigate(Routes.taskDetail(it)) }
        )
    }
    composable(Routes.PROFILE) {
        ProfileScreen(onBack = { navController.popBackStack() })
    }
    composable(Routes.ABOUT) {
        AboutScreen(onBack = { navController.popBackStack() })
    }

    // ── Expenses ───────────────────────────────────────────────────────────────
    composable(Routes.EXPENSES) {
        ExpensesScreen(
            onBack = { navController.popBackStack() },
            onAddExpense = { navController.navigate(Routes.addExpense()) },
            onEditExpense = { navController.navigate(Routes.editExpense(it)) }
        )
    }
    composable(
        route = Routes.EXPENSE_FORM,
        arguments = listOf(
            navArgument(Routes.ARG_EXPENSE_ID) { type = NavType.LongType; defaultValue = -1L }
        )
    ) {
        ExpenseFormScreen(
            onBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() }
        )
    }

    // ── Pet management ──────────────────────────────────────────────────────────
    composable(
        route = Routes.PET_FORM,
        arguments = listOf(navArgument(Routes.ARG_PET_ID) { type = NavType.LongType; defaultValue = -1L })
    ) {
        PetFormScreen(
            onBack = { navController.popBackStack() },
            onSaved = { petId, wasNew ->
                if (wasNew) {
                    navController.navigate(Routes.petDetail(petId)) {
                        popUpTo(Routes.PET_FORM) { inclusive = true }
                    }
                } else navController.popBackStack()
            }
        )
    }
    composable(
        route = Routes.PET_DETAIL,
        arguments = listOf(navArgument(Routes.ARG_PET_ID) { type = NavType.LongType })
    ) {
        PetProfileScreen(
            onBack = { navController.popBackStack() },
            onEdit = { navController.navigate(Routes.editPet(it)) },
            onDeleted = { navController.popBackStack() },
            onOpenTask = { navController.navigate(Routes.taskDetail(it)) },
            onAddTask = { navController.navigate(Routes.addTask(it)) },
            onDelegate = { navController.navigate(Routes.delegate(it)) }
        )
    }

    // ── Task management ─────────────────────────────────────────────────────────
    composable(
        route = Routes.TASK_FORM,
        arguments = listOf(
            navArgument(Routes.ARG_TASK_ID) { type = NavType.LongType; defaultValue = -1L },
            navArgument(Routes.ARG_PET_ID) { type = NavType.LongType; defaultValue = -1L },
            navArgument(Routes.ARG_START_EPOCH_DAY) { type = NavType.LongType; defaultValue = -1L }
        )
    ) {
        TaskFormScreen(
            onBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() },
            onAddPet = { navController.navigate(Routes.addPet()) }
        )
    }
    composable(
        route = Routes.TASK_DETAIL,
        arguments = listOf(navArgument(Routes.ARG_TASK_ID) { type = NavType.LongType })
    ) {
        TaskDetailScreen(
            onBack = { navController.popBackStack() },
            onEdit = { navController.navigate(Routes.editTask(it)) },
            onDeleted = { navController.popBackStack() },
            onOpenPet = { navController.navigate(Routes.petDetail(it)) },
            onDelegate = { navController.navigate(Routes.delegate(it)) }
        )
    }

    // ── Delegation ──────────────────────────────────────────────────────────────
    composable(
        route = Routes.DELEGATE,
        arguments = listOf(navArgument(Routes.ARG_PET_ID) { type = NavType.LongType })
    ) {
        DelegateScreen(
            onBack = { navController.popBackStack() },
            onContinue = { petId, flags, phone ->
                navController.navigate(Routes.delegatePreview(petId, flags, phone))
            }
        )
    }
    composable(
        route = Routes.DELEGATE_PREVIEW,
        arguments = listOf(
            navArgument(Routes.ARG_PET_ID) { type = NavType.LongType },
            navArgument(Routes.ARG_FLAGS) { type = NavType.StringType },
            navArgument(Routes.ARG_PHONE) { type = NavType.StringType }
        )
    ) {
        SmsPreviewScreen(
            onBack = { navController.popBackStack() },
            onDone = { navController.popBackStack(Routes.DELEGATE, inclusive = true) }
        )
    }
}
