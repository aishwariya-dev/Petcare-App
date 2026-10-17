package com.petcare.app.ui.screens.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petcare.app.model.Frequency
import com.petcare.app.model.TaskCategory
import com.petcare.app.ui.common.LocalFeedback
import com.petcare.app.ui.common.appViewModel
import com.petcare.app.ui.components.DateField
import com.petcare.app.ui.components.DropdownField
import com.petcare.app.ui.components.EmptyState
import com.petcare.app.ui.components.ErrorState
import com.petcare.app.ui.components.FormTextField
import com.petcare.app.ui.components.ImagePickerField
import com.petcare.app.ui.components.LoadingState
import com.petcare.app.ui.components.PetCareTopBar
import com.petcare.app.ui.components.SectionHeader
import com.petcare.app.ui.components.TimeField
import com.petcare.app.ui.theme.Spacing
import com.petcare.app.util.ImageStorage

/** Screens 12 and 14. */
@Composable
fun TaskFormScreen(onBack: () -> Unit, onSaved: () -> Unit, onAddPet: () -> Unit) {
    val viewModel = appViewModel { container, handle ->
        TaskFormViewModel(
            container.authRepository,
            container.petRepository,
            container.taskRepository,
            container.imageStorage,
            handle,
            container.preferences
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val smsConsent by viewModel.smsConsent.collectAsStateWithLifecycle()
    val feedback = LocalFeedback.current
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPets() }

    val saving = state.isSaving || state.saved
    val handleBackNavigation = {
        if (!saving) {
            state.pendingImageUri?.let { ImageStorage.deleteCapture(context, it) }
            onBack()
        }
    }
    BackHandler(onBack = handleBackNavigation)

    val loadError = state.loadError

    Scaffold(topBar = {
        PetCareTopBar(
            title = if (state.isEdit) "Edit task" else "Create care task",
            onBack = handleBackNavigation
        )
    }) { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            loadError != null -> ErrorState(
                loadError,
                Modifier.padding(padding)
            )
            state.pets.isEmpty() -> Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                EmptyState(
                    icon = Icons.Outlined.Pets,
                    title = "Add a pet first",
                    message = "Every care task belongs to a pet.",
                    actionLabel = "Add pet",
                    onAction = onAddPet
                )
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = Spacing.medium)
            ) {
                SectionHeader("Task")
                DropdownField(
                    value = state.pet,
                    options = state.pets,
                    onSelect = viewModel::onPetChange,
                    label = "Pet",
                    optionLabel = { it.name },
                    required = true,
                    error = state.petError,
                    enabled = !saving
                )
                Spacer(Modifier.height(Spacing.small))
                FormTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = "Task name",
                    required = true,
                    error = state.nameError,
                    placeholder = "Morning walk",
                    enabled = !saving
                )
                Spacer(Modifier.height(Spacing.small))
                DropdownField(
                    value = state.category,
                    options = TaskCategory.entries,
                    onSelect = viewModel::onCategoryChange,
                    label = "Category",
                    optionLabel = { it.label },
                    enabled = !saving
                )

                SectionHeader("Schedule")
                DropdownField(
                    value = state.frequency,
                    options = Frequency.entries,
                    onSelect = viewModel::onFrequencyChange,
                    label = "Frequency",
                    optionLabel = { "${it.label} — ${it.description}" },
                    enabled = !saving
                )
                Spacer(Modifier.height(Spacing.small))
                DateField(
                    value = state.date,
                    onSelect = viewModel::onDateChange,
                    label = state.dateLabel,
                    required = true,
                    error = state.dateError,
                    enabled = !saving
                )
                Spacer(Modifier.height(Spacing.small))
                TimeField(
                    value = state.time,
                    onSelect = viewModel::onTimeChange,
                    label = "Time",
                    required = true,
                    error = state.timeError,
                    enabled = !saving
                )
                val scheduleHint = state.date?.let { date ->
                    when (state.frequency) {
                        Frequency.WEEKLY ->
                            "Repeats every ${date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}"
                        Frequency.MONTHLY ->
                            "Repeats monthly on day ${date.dayOfMonth} (short months use their last day)"
                        else -> null
                    }
                }
                scheduleHint?.let { hint ->
                    Text(
                        hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs)
                    )
                }

                TaskRemindersCard(
                    reminderEnabled = state.reminderEnabled,
                    smsEnabled = state.smsReminderEnabled,
                    recipient = state.smsRecipient,
                    recipientError = state.smsRecipientError,
                    time = state.time,
                    smsConsent = smsConsent,
                    onReminderEnabledChange = viewModel::onReminderEnabledChange,
                    onSmsEnabledChange = viewModel::onSmsReminderEnabledChange,
                    onRecipientChange = viewModel::onSmsRecipientChange,
                    savedRecipients = state.savedSmsRecipients,
                    onForgetRecipient = viewModel::forgetSmsRecipient,
                    onGrantSmsConsent = viewModel::grantSmsConsent,
                    interactionEnabled = !saving
                )
                Spacer(Modifier.height(Spacing.medium))

                SectionHeader("Details")
                FormTextField(
                    value = state.supplies,
                    onValueChange = viewModel::onSuppliesChange,
                    label = "Required supplies",
                    placeholder = "Leash, water",
                    supportingText = "Separate items with commas",
                    enabled = !saving
                )
                Spacer(Modifier.height(Spacing.small))
                FormTextField(
                    value = state.notes,
                    onValueChange = viewModel::onNotesChange,
                    label = "Notes",
                    placeholder = "30 minute walk around the park",
                    singleLine = false,
                    minLines = 3,
                    enabled = !saving
                )
                Spacer(Modifier.height(Spacing.medium))
                ImagePickerField(
                    imagePath = state.visibleImagePath,
                    pendingUri = state.pendingImageUri,
                    onImagePicked = viewModel::onImagePicked,
                    onRemove = viewModel::onImageRemoved,
                    label = "Add image",
                    circular = false,
                    enabled = !saving,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                state.formError?.let { formError ->
                    Spacer(Modifier.height(Spacing.small))
                    Text(
                        formError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(Modifier.height(Spacing.large))
                Button(
                    onClick = {
                        viewModel.save()
                    },
                    enabled = !saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(if (state.isEdit) "Save changes" else "Create task")
                    }
                }
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }

    state.reminderPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Reminder preview") },
            text = {
                Column {
                    Text(
                        "Preview only — PetCare has not sent an SMS or posted a notification.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(Spacing.medium))
                    Text("Local notification", style = MaterialTheme.typography.titleSmall)
                    if (!preview.localNotificationEnabled) {
                        Text(
                            "Local notifications are off for this task.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(preview.notification.title, style = MaterialTheme.typography.titleMedium)
                    Text(preview.notification.body)
                    Text(
                        "Scheduled time: ${preview.notification.time}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    preview.sms?.let { sms ->
                        Spacer(Modifier.height(Spacing.medium))
                        Text("Automatic SMS preview", style = MaterialTheme.typography.titleSmall)
                        Text("Recipient: ${sms.recipient}")
                        Text(sms.message)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        feedback.show(if (state.isEdit) "Task updated" else "Task created")
                        onSaved()
                    }
                ) { Text("Done") }
            }
        )
    }
}
