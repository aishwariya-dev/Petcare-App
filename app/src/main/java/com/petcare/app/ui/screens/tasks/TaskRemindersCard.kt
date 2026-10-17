package com.petcare.app.ui.screens.tasks

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.petcare.app.permissions.SmsPermission
import com.petcare.app.ui.common.rememberAppContainer
import com.petcare.app.ui.components.FormTextField
import com.petcare.app.ui.components.SectionHeader
import com.petcare.app.ui.screens.permissions.SMS_BLOCKED_GUIDANCE
import com.petcare.app.ui.screens.permissions.SmsConsentDialog
import com.petcare.app.ui.theme.Spacing
import com.petcare.app.util.DateTimeFormats
import java.time.LocalTime

/**
 * The task form's one "Reminders" card: a notification row and an SMS row.
 * System permissions are asked only when a switch is turned on, and a
 * refused switch simply stays off.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskRemindersCard(
    reminderEnabled: Boolean,
    smsEnabled: Boolean,
    recipient: String,
    recipientError: String?,
    time: LocalTime?,
    smsConsent: Boolean,
    onReminderEnabledChange: (Boolean) -> Unit,
    onSmsEnabledChange: (Boolean) -> Unit,
    onRecipientChange: (String) -> Unit,
    onGrantSmsConsent: () -> Unit,
    modifier: Modifier = Modifier,
    savedRecipients: List<String> = emptyList(),
    onForgetRecipient: (String) -> Unit = {},
    interactionEnabled: Boolean = true
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val permissions = rememberAppContainer().devicePermissions

    var notificationsOffDialog by remember { mutableStateOf(false) }
    var smsBlockedDialog by remember { mutableStateOf(false) }
    var consentDialog by remember { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) onReminderEnabledChange(true) else notificationsOffDialog = true }

    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onSmsEnabledChange(true)
        } else {
            permissions.recordSmsDenied()
            if (permissions.sms(activity) == SmsPermission.BLOCKED) smsBlockedDialog = true
        }
    }

    fun turnOnReminder() {
        when {
            permissions.notificationsAllowed() -> onReminderEnabledChange(true)
            permissions.canRequestNotifications() -> notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> notificationsOffDialog = true
        }
    }

    fun requestSmsAfterConsent() {
        when (permissions.sms(activity)) {
            SmsPermission.GRANTED -> onSmsEnabledChange(true)
            SmsPermission.NOT_GRANTED -> smsLauncher.launch(Manifest.permission.SEND_SMS)
            SmsPermission.BLOCKED -> smsBlockedDialog = true
            SmsPermission.UNSUPPORTED -> Unit
        }
    }

    fun turnOnSms() {
        if (permissions.sms(activity) == SmsPermission.BLOCKED) {
            smsBlockedDialog = true
        } else if (!smsConsent) {
            consentDialog = true
        } else {
            requestSmsAfterConsent()
        }
    }

    val smsSupported = remember { permissions.sms(activity) != SmsPermission.UNSUPPORTED }

    Column(modifier = modifier) {
        SectionHeader("Reminders")
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(Spacing.medium)) {
                ReminderRow(
                    icon = Icons.Outlined.Notifications,
                    title = "Remind me",
                    subtitle = "A notification at the task time.",
                    checked = reminderEnabled,
                    enabled = interactionEnabled,
                    onCheckedChange = { checked ->
                        if (checked) turnOnReminder() else onReminderEnabledChange(false)
                    }
                )
                if (smsSupported) {
                    HorizontalDivider(Modifier.padding(vertical = Spacing.small))
                    ReminderRow(
                        icon = Icons.Outlined.Sms,
                        title = "Also send SMS",
                        subtitle = "Uses the task time · may cost 1–3 SMS",
                        checked = smsEnabled,
                        enabled = interactionEnabled,
                        onCheckedChange = { checked ->
                            if (checked) turnOnSms() else onSmsEnabledChange(false)
                        }
                    )
                    if (smsEnabled) {
                        Spacer(Modifier.height(Spacing.small))
                        FormTextField(
                            value = recipient,
                            onValueChange = onRecipientChange,
                            label = "Recipient phone number",
                            required = true,
                            error = recipientError,
                            keyboardType = KeyboardType.Phone,
                            enabled = interactionEnabled
                        )
                        if (savedRecipients.isNotEmpty()) {
                            Text(
                                "Saved numbers",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Spacing.small)
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                                savedRecipients.forEach { number ->
                                    InputChip(
                                        selected = number == recipient.trim(),
                                        onClick = { onRecipientChange(number) },
                                        label = { Text(number) },
                                        enabled = interactionEnabled,
                                        trailingIcon = {
                                            Icon(
                                                Icons.Outlined.Close,
                                                contentDescription = "Forget $number",
                                                modifier = Modifier
                                                    .size(InputChipDefaults.IconSize)
                                                    .clickable(enabled = interactionEnabled) { onForgetRecipient(number) }
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                if (reminderEnabled || smsEnabled) {
                    Spacer(Modifier.height(Spacing.small))
                    Text(
                        text = if (time == null) "Choose a time above before saving this reminder."
                        else "Reminder time: ${DateTimeFormats.time(time)}",
                        color = if (time == null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    if (notificationsOffDialog) {
        AlertDialog(
            onDismissRequest = { notificationsOffDialog = false },
            title = { Text("Notifications are off") },
            text = { Text("PetCare cannot show reminders until notifications are allowed for it in system settings.") },
            confirmButton = {
                TextButton(onClick = {
                    notificationsOffDialog = false
                    permissions.openNotificationSettings(context)
                }) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = { notificationsOffDialog = false }) { Text("Not now") } }
        )
    }
    if (smsBlockedDialog) {
        AlertDialog(
            onDismissRequest = { smsBlockedDialog = false },
            title = { Text("SMS is blocked by Android") },
            text = { Text(SMS_BLOCKED_GUIDANCE) },
            confirmButton = {
                TextButton(onClick = {
                    smsBlockedDialog = false
                    permissions.openAppSettings(context)
                }) { Text("Open app settings") }
            },
            dismissButton = { TextButton(onClick = { smsBlockedDialog = false }) { Text("Not now") } }
        )
    }
    if (consentDialog) {
        SmsConsentDialog(
            onAgree = {
                consentDialog = false
                onGrantSmsConsent()
                requestSmsAfterConsent()
            },
            onDismiss = { consentDialog = false }
        )
    }
}

@Composable
private fun ReminderRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.medium)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
