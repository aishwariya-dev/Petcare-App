package com.petcare.app.ui.screens.permissions

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petcare.app.permissions.DevicePermissions
import com.petcare.app.permissions.SmsPermission
import com.petcare.app.ui.common.appViewModel
import com.petcare.app.ui.common.rememberAppContainer
import com.petcare.app.ui.components.PetCareTopBar
import com.petcare.app.ui.theme.Spacing

/**
 * Shown once per phone after the first sign-in and again from Settings →
 * Reminders. Nothing here is required: every row can be skipped and fixed later.
 */
@Composable
fun PermissionsScreen(
    onDone: () -> Unit,
    devicePermissions: DevicePermissions? = null
) {
    val viewModel = appViewModel { c, _ -> PermissionsViewModel(c.authRepository, c.preferences) }
    val container = rememberAppContainer()
    val permissions = devicePermissions ?: container.devicePermissions
    val smsConsent by viewModel.smsConsent.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity

    // Rows re-read device state whenever the user comes back from a system page.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val state = remember(refresh, smsConsent) {
        PermissionRows(
            notifications = permissions.notificationsAllowed(),
            exactAlarms = permissions.exactAlarmsAllowed(),
            exactAlarmsConfigurable = permissions.exactAlarmsConfigurable(),
            sms = permissions.sms(activity),
            battery = permissions.batteryUnrestricted()
        )
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (!granted) permissions.openNotificationSettings(context); refresh++ }
    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (!granted) permissions.recordSmsDenied(); refresh++ }

    var consentOpen by remember { mutableStateOf(false) }

    fun finish() {
        viewModel.markIntroSeen()
        onDone()
    }

    Scaffold(topBar = { PetCareTopBar(title = "Permissions") }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.small)
        ) {
            Text(
                "Let reminders reach you",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = Spacing.small)
            )
            Text(
                "PetCare works offline on this phone. These settings only decide how reminders are delivered.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.xs))

            PermissionRow(
                icon = Icons.Outlined.Notifications,
                title = "Notifications",
                reason = "so reminders can appear",
                status = if (state.notifications) RowStatus.Allowed else RowStatus.NotAllowed,
                actionLabel = "Allow",
                onAction = {
                    if (permissions.canRequestNotifications()) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        permissions.openNotificationSettings(context)
                    }
                }
            )

            if (state.exactAlarmsConfigurable && !state.exactAlarms) {
                PermissionRow(
                    icon = Icons.Outlined.Schedule,
                    title = "Precise timing",
                    reason = "so reminders fire at the exact minute",
                    status = RowStatus.NotAllowed,
                    actionLabel = "Allow",
                    onAction = { permissions.openExactAlarmSettings(context) }
                )
            }

            if (state.sms != SmsPermission.UNSUPPORTED) {
                PermissionRow(
                    icon = Icons.Outlined.Sms,
                    title = "SMS reminders",
                    reason = "so PetCare can send a task reminder by SMS at the scheduled time",
                    status = when (state.sms) {
                        SmsPermission.GRANTED -> RowStatus.Allowed
                        SmsPermission.BLOCKED -> RowStatus.Blocked
                        else -> RowStatus.NotAllowed
                    },
                    detail = if (state.sms == SmsPermission.BLOCKED) SMS_BLOCKED_GUIDANCE else null,
                    actionLabel = if (state.sms == SmsPermission.BLOCKED) "Open app settings" else "Allow",
                    onAction = {
                        when {
                            state.sms == SmsPermission.BLOCKED -> permissions.openAppSettings(context)
                            !smsConsent -> consentOpen = true
                            else -> smsLauncher.launch(Manifest.permission.SEND_SMS)
                        }
                    }
                )
            }

            if (!state.battery) {
                PermissionRow(
                    icon = Icons.Outlined.BatteryChargingFull,
                    title = "Background running",
                    reason = "some phones stop background apps to save battery, and then reminders never arrive",
                    status = RowStatus.NotAllowed,
                    actionLabel = "Allow",
                    onAction = { permissions.requestBatteryUnrestricted(context) }
                )
            }

            Spacer(Modifier.height(Spacing.medium))
            Button(onClick = ::finish, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
            TextButton(onClick = ::finish, modifier = Modifier.fillMaxWidth()) { Text("Not now") }
            Spacer(Modifier.height(Spacing.xl))
        }
    }

    if (consentOpen) {
        SmsConsentDialog(
            onAgree = {
                consentOpen = false
                viewModel.grantSmsConsent()
                smsLauncher.launch(Manifest.permission.SEND_SMS)
            },
            onDismiss = { consentOpen = false }
        )
    }
}

private data class PermissionRows(
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val exactAlarmsConfigurable: Boolean,
    val sms: SmsPermission,
    val battery: Boolean
)

private enum class RowStatus(val label: String) {
    Allowed("Allowed"),
    NotAllowed("Not allowed"),
    Blocked("Blocked by Android")
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    reason: String,
    status: RowStatus,
    actionLabel: String,
    onAction: () -> Unit,
    detail: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Column(Modifier.padding(Spacing.medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = Spacing.medium)
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusLabel(status)
            }
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.small)
                )
            }
            if (status != RowStatus.Allowed) {
                Spacer(Modifier.height(Spacing.small))
                FilledTonalButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun StatusLabel(status: RowStatus) {
    val colour = when (status) {
        RowStatus.Allowed -> MaterialTheme.colorScheme.primary
        RowStatus.NotAllowed -> MaterialTheme.colorScheme.onSurfaceVariant
        RowStatus.Blocked -> MaterialTheme.colorScheme.error
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (status == RowStatus.Allowed) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = colour,
                modifier = Modifier
                    .size(16.dp)
                    .padding(end = 2.dp)
            )
        }
        Text(status.label, style = MaterialTheme.typography.labelMedium, color = colour)
    }
}

const val SMS_BLOCKED_GUIDANCE =
    "Android blocks SMS for apps installed outside the Play Store. In App info tap ⋮ (top right) → " +
        "Allow restricted settings, then Permissions → SMS → Allow."
