package com.petcare.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Switch
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.petcare.app.ui.common.rememberAppContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import com.petcare.app.BuildConfig
import com.petcare.app.model.ThemeMode
import com.petcare.app.ui.common.appViewModel
import com.petcare.app.ui.components.ConfirmationDialog
import com.petcare.app.ui.components.PetCareTopBar
import com.petcare.app.ui.components.SectionHeader
import com.petcare.app.ui.components.UserProfileSummaryCard
import com.petcare.app.ui.theme.Spacing

/** Screens 23 (settings) and 24 (logout confirmation). */
@Composable
fun SettingsScreen(
    onOpenProfile: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenAbout: () -> Unit,
    onLoggedOut: () -> Unit
) {
    val viewModel = appViewModel { c, _ ->
        SettingsViewModel(c.authRepository, c.preferences, c.petRepository)
    }
    val user by viewModel.user.collectAsStateWithLifecycle()
    val petCount by viewModel.petCount.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val remindersEnabled by viewModel.remindersEnabled.collectAsStateWithLifecycle()
    val devicePermissions = rememberAppContainer().devicePermissions
    // Re-read notification access when the user returns from a system page.
    var permissionRefresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionRefresh++ }
    val notificationsAllowed = remember(permissionRefresh) { devicePermissions.notificationsAllowed() }
    var confirmLogout by remember { mutableStateOf(false) }
    var loggingOut by remember { mutableStateOf(false) }
    var appearanceOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            PetCareTopBar(
                title = "Settings",
                profileName = user?.name,
                profilePhotoPath = user?.photoPath,
                onProfile = onOpenProfile
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SectionHeader("Account", modifier = Modifier.padding(horizontal = Spacing.medium))
            user?.let {
                UserProfileSummaryCard(
                    user = it,
                    petCount = petCount,
                    onViewProfile = onOpenProfile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.medium)
                )
            }


            SectionHeader("Reminders", modifier = Modifier.padding(horizontal = Spacing.medium))
            SettingsGroup {
                SettingsItem(
                    icon = Icons.Outlined.Notifications,
                    title = "Reminders",
                    subtitle = when {
                        !remindersEnabled -> "Off"
                        notificationsAllowed -> "On · notifications allowed"
                        else -> "On · needs permission — tap to fix"
                    },
                    trailingContent = {
                        Switch(
                            checked = remindersEnabled,
                            onCheckedChange = viewModel::setRemindersEnabled,
                            modifier = Modifier.semantics { contentDescription = "Reminders switch" }
                        )
                    },
                    onClick = onOpenPermissions
                )
            }

            SectionHeader("App", modifier = Modifier.padding(horizontal = Spacing.medium))
            SettingsGroup { SettingsItem(
                icon = Icons.Outlined.DarkMode,
                title = "Appearance",
                subtitle = themeMode.label,
                onClick = { appearanceOpen = true }
            ) }

            SectionHeader("About", modifier = Modifier.padding(horizontal = Spacing.medium))
            SettingsGroup {
                SettingsItem(
                    icon = Icons.Outlined.Info,
                    title = "About PetCare",
                    subtitle = "Version ${BuildConfig.VERSION_NAME}",
                    onClick = onOpenAbout
                )
            }

            Spacer(Modifier.height(Spacing.large))
            SettingsGroup {
                ListItem(
                    headlineContent = { Text("Log out", color = MaterialTheme.colorScheme.error) },
                    leadingContent = {
                        Icon(
                            Icons.AutoMirrored.Outlined.Logout,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    ),
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { if (!loggingOut) confirmLogout = true }
                )
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }

    if (confirmLogout) {
        ConfirmationDialog(
            title = "Log out?",
            message = "You can log back in anytime.",
            confirmLabel = "Log out",
            onConfirm = {
                confirmLogout = false
                loggingOut = true
                viewModel.logout {
                    onLoggedOut()
                }
            },
            onDismiss = { confirmLogout = false }
        )
    }

    if (appearanceOpen) {
        AlertDialog(
            onDismissRequest = { appearanceOpen = false },
            title = { Text("Appearance") },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode); appearanceOpen = false },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = themeMode == mode, onClick = null)
                            Text(mode.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { appearanceOpen = false }) { Text("Done") } }
        )
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = leadingContent ?: {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(10.dp)).padding(8.dp))
        },
        trailingContent = trailingContent ?: { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
    )
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.medium),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) { content() }
}
