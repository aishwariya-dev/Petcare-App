package com.petcare.app.ui.screens.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.petcare.app.model.categoryType
import com.petcare.app.model.frequencyType
import com.petcare.app.model.startDate
import com.petcare.app.model.subtitle
import com.petcare.app.model.supplyList
import androidx.compose.ui.platform.LocalContext
import com.petcare.app.domain.TaskSchedule
import com.petcare.app.reminder.TaskSmsTemplate
import com.petcare.app.util.ShareLauncher
import com.petcare.app.util.SmsLauncher
import java.time.LocalDate
import androidx.compose.foundation.layout.Arrangement
import com.petcare.app.ui.common.LocalFeedback
import com.petcare.app.ui.common.UiState
import com.petcare.app.ui.common.appViewModel
import com.petcare.app.ui.components.ConfirmationDialog
import com.petcare.app.ui.components.ErrorState
import com.petcare.app.ui.components.LoadingState
import com.petcare.app.ui.components.PetAvatar
import com.petcare.app.ui.components.PetCareTopBar
import com.petcare.app.ui.components.SectionHeader
import com.petcare.app.ui.theme.Spacing
import com.petcare.app.util.DateTimeFormats
import java.io.File

/** Screens 13, 15, 16. */
@Composable
fun TaskDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDeleted: () -> Unit,
    onOpenPet: (Long) -> Unit,
    onDelegate: (Long) -> Unit
) {
    val viewModel = appViewModel { c, handle ->
        TaskDetailViewModel(c.taskRepository, c.petRepository, handle, c.smsReminderRepository)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val feedback = LocalFeedback.current

    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleteInProgress by remember { mutableStateOf(false) }

    LaunchedEffect(deleted) {
        if (deleted) {
            feedback.show("Task deleted")
            onDeleted()
        }
    }

    val data = (state as? UiState.Success)?.data

    Scaffold(
        topBar = {
            PetCareTopBar(title = "Task", onBack = onBack) {
                if (data != null && !deleteInProgress) {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit task") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                            onClick = { menuOpen = false; onEdit(data.task.id) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delegate ${data.pet.name}'s care") },
                            leadingIcon = { Icon(Icons.Outlined.Share, null) },
                            onClick = { menuOpen = false; onDelegate(data.pet.id) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete task", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menuOpen = false; confirmDelete = true }
                        )
                    }
                }
            }
        }
    ) { padding ->
        when (val s = state) {
            UiState.Loading -> LoadingState(Modifier.padding(padding))
            is UiState.Error -> ErrorState(s.message, Modifier.padding(padding))
            is UiState.Success -> {
                val d = s.data
                if (d == null) {
                    if (!deleted) ErrorState("This task no longer exists.", Modifier.padding(padding))
                } else {
                    DetailContent(
                        data = d,
                        modifier = Modifier.padding(padding),
                        onToggle = {
                            val next = !d.completedToday
                            viewModel.setCompleted(
                                task = d.task,
                                completed = next,
                                onDone = {
                                    feedback.show(
                                        if (next) "\"${d.task.name}\" completed"
                                        else "\"${d.task.name}\" marked not done"
                                    )
                                },
                                onError = { feedback.show(it) }
                            )
                        },
                        onEdit = { onEdit(d.task.id) },
                        onOpenPet = { onOpenPet(d.pet.id) }
                    )
                }
            }
        }
    }

    if (confirmDelete && data != null) {
        ConfirmationDialog(
            title = "Delete task?",
            message = "\"${data.task.name}\" will be removed from ${data.pet.name}'s routine.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                deleteInProgress = true
                viewModel.delete(data.task) {
                    deleteInProgress = false
                    feedback.show(it)
                }
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun DetailContent(
    data: TaskDetailData,
    modifier: Modifier,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onOpenPet: () -> Unit
) {
    val task = data.task
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.medium)
    ) {
        Spacer(Modifier.height(Spacing.small))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(Spacing.medium)) {
                androidx.compose.material3.Text(task.categoryType.label, style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                Text(task.name, style = MaterialTheme.typography.headlineSmall)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onOpenPet)
                .padding(vertical = Spacing.small)
        ) {
            PetAvatar(pet = data.pet, size = 44.dp)
            Spacer(Modifier.width(Spacing.medium))
            Column {
                Text(data.pet.name, style = MaterialTheme.typography.titleMedium)
                Text(data.pet.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } } }

        Spacer(Modifier.height(Spacing.medium))
        StatusCard(data)

        SectionHeader("Schedule")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = MaterialTheme.shapes.medium) { Column(Modifier.padding(Spacing.medium)) {
        DetailRow(Icons.Outlined.Category, task.categoryType.label)
        DetailRow(Icons.Outlined.Repeat, task.frequencyType.description)
        DetailRow(
            Icons.Outlined.CalendarMonth,
            when (task.frequencyType.name) {
                "ONCE" -> DateTimeFormats.fullDate(task.startDate)
                "WEEKLY" -> "Every ${task.startDate.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }} from ${DateTimeFormats.fullDate(task.startDate)}"
                "MONTHLY" -> "Monthly on day ${task.startDate.dayOfMonth} from ${DateTimeFormats.fullDate(task.startDate)}"
                else -> "Since ${DateTimeFormats.fullDate(task.startDate)}"
            }
        )
        if (task.smsReminderEnabled) {
            DetailRow(Icons.Outlined.Sms, "Automatic SMS: ${task.smsRecipient}")
        }
        data.smsStatus?.let { DetailRow(Icons.Outlined.Sms, "SMS status: $it") }
        DetailRow(Icons.Outlined.Schedule, DateTimeFormats.time(task.timeMinutes))
        DetailRow(
            Icons.Outlined.Notifications,
            if (task.reminderEnabled) "Reminder enabled" else "No reminder"
        )
        } }

        val supplies = task.supplyList
        if (supplies.isNotEmpty()) {
            SectionHeader("Supplies")
            DetailCard { supplies.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp)) } }
        }

        if (task.notes.isNotBlank()) {
            SectionHeader("Notes")
            DetailCard { Text(task.notes, style = MaterialTheme.typography.bodyMedium) }
        }

        val image = task.imagePath?.let { File(it) }?.takeIf { it.exists() }
        if (image != null) {
            SectionHeader("Image")
            AsyncImage(
                model = image,
                contentDescription = "Task image",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(MaterialTheme.shapes.medium)
            )
        }

        Spacer(Modifier.height(Spacing.xl))
        // A task can only receive a new completion for an occurrence that is
        // scheduled today. Keep Undo available for a completion recorded
        // today, including an overdue one-off whose occurrence leaves the
        // checklist immediately after completion.
        val canToggle = data.completedToday || data.occursToday
        Button(
            onClick = onToggle,
            enabled = canToggle,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.large,
            colors = if (data.completedToday) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.buttonColors()
        ) {
            Icon(if (data.completedToday) Icons.Outlined.RadioButtonUnchecked else Icons.Filled.CheckCircle, null)
            Spacer(Modifier.width(Spacing.small))
            Text(if (data.completedToday) "Mark as not done" else "Mark as done")
        }
        Spacer(Modifier.height(Spacing.small))
        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Outlined.Edit, null)
            Spacer(Modifier.width(Spacing.small))
            Text("Edit task")
        }
        Spacer(Modifier.height(Spacing.small))
        // Manual sending never needs the SMS permission: Messages or the share
        // sheet does the sending, so this works even when automatic SMS is blocked.
        val context = LocalContext.current
        val feedback = LocalFeedback.current
        val today = LocalDate.now()
        val smsText = TaskSmsTemplate.build(
            task = task,
            pet = data.pet,
            occurrence = TaskSchedule.nextOccurrenceOnOrAfter(task, today) ?: today,
            today = today
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            OutlinedButton(
                onClick = {
                    if (!SmsLauncher.open(context, task.smsRecipient, smsText)) {
                        feedback.show("No messaging app is available on this device")
                    }
                },
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Icon(Icons.Outlined.Sms, null)
                Spacer(Modifier.width(Spacing.xs))
                Text("Send SMS")
            }
            OutlinedButton(
                onClick = {
                    if (!ShareLauncher.shareText(context, smsText, "Share task")) {
                        feedback.show("No app is available to share this text")
                    }
                },
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Icon(Icons.Outlined.Share, null)
                Spacer(Modifier.width(Spacing.xs))
                Text("Share")
            }
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}

@Composable
private fun DetailCard(content: @Composable () -> Unit) {
    Card(shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(Spacing.medium)) { content() }
    }
}

@Composable
private fun StatusCard(data: TaskDetailData) {
    val (label, container, content) = when {
        data.completedToday -> Triple(
            if (data.task.frequencyType.name == "ONCE") "Completed" else "Completed today",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        data.overdue -> Triple("Overdue", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        data.occursToday -> Triple("Pending today", MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface)
        else -> Triple("Not scheduled today", MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Card(colors = CardDefaults.cardColors(containerColor = container)) {
        Row(Modifier.fillMaxWidth().padding(Spacing.medium), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (data.completedToday) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = content
            )
            Spacer(Modifier.width(Spacing.medium))
            Text(label, style = MaterialTheme.typography.titleMedium, color = content)
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Spacing.medium))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
