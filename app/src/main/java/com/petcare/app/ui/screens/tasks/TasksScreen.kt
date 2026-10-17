package com.petcare.app.ui.screens.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petcare.app.model.CareTask
import com.petcare.app.model.UpcomingItem
import com.petcare.app.model.categoryType
import com.petcare.app.ui.common.LocalFeedback
import com.petcare.app.ui.common.UiState
import com.petcare.app.ui.common.appViewModel
import com.petcare.app.ui.components.ConfirmationDialog
import com.petcare.app.ui.components.EmptyState
import com.petcare.app.ui.components.ErrorState
import com.petcare.app.ui.components.LoadingState
import com.petcare.app.ui.components.PetAvatar
import com.petcare.app.ui.components.PetCareTopBar
import com.petcare.app.ui.components.SectionHeader
import com.petcare.app.ui.components.SwipeableTaskCard
import com.petcare.app.ui.theme.Spacing
import com.petcare.app.util.DateTimeFormats
import com.petcare.app.util.ShakeListener

/**
 * Screens 11, 15, 16, 17, 18, 19.
 * [onCreateTask] receives a pre-filled start day: null (today) from Today, tomorrow from Upcoming.
 */
@Composable
fun TasksScreen(onOpenTask: (Long) -> Unit, onCreateTask: (Long?) -> Unit, onAddPet: () -> Unit) {
    val viewModel = appViewModel { c, handle ->
        TasksViewModel(c.authRepository, c.petRepository, c.taskRepository, handle)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val feedback = LocalFeedback.current

    var taskToDelete by remember { mutableStateOf<CareTask?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var resetInProgress by remember { mutableStateOf(false) }

    val hasCompletedToday = (state as? UiState.Success)?.data?.todayGroups?.any { it.completedIds.isNotEmpty() } == true

    // Shake to reset (screen 19). Only listens while this screen is visible and there is something to reset.
    ShakeListener(enabled = tab == TaskTab.TODAY && !showResetDialog && taskToDelete == null && !resetInProgress) {
        if (hasCompletedToday) showResetDialog = true
        else feedback.show("Nothing to reset — no tasks completed today")
    }

    fun performDelete(task: CareTask) {
        viewModel.delete(
            task,
            onDeleted = {
                feedback.show(
                    message = "\"${task.name}\" deleted",
                    actionLabel = "Undo",
                    onAction = {
                        viewModel.restore(task,
                            onRestored = { feedback.show("\"${task.name}\" restored") },
                            onError = { feedback.show(it) })
                    },
                    onDismissed = { viewModel.discardImage(task) { feedback.show(it) } }
                )
            },
            onError = { feedback.show(it) }
        )
    }

    Scaffold(
        topBar = { PetCareTopBar(title = "Care tasks") },
        floatingActionButton = {
            val data = (state as? UiState.Success)?.data
            // Both tabs can add a task once there is a pet; the empty states
            // keep their own inline action as well.
            val showFab = data != null && data.hasPets
            if (showFab) {
                FloatingActionButton(onClick = { onCreateTask(createStartDay(tab)) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Create care task")
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.medium, vertical = Spacing.small)
            ) {
                TaskTab.entries.forEachIndexed { index, t ->
                    SegmentedButton(
                        selected = tab == t,
                        onClick = { viewModel.selectTab(t) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = TaskTab.entries.size)
                    ) { Text(t.label) }
                }
            }

            when (val s = state) {
                UiState.Loading -> LoadingState()
                is UiState.Error -> ErrorState(s.message)
                is UiState.Success -> when (tab) {
                    TaskTab.TODAY -> TodayList(
                        data = s.data,
                        onOpenTask = onOpenTask,
                        onCreateTask = { onCreateTask(createStartDay(TaskTab.TODAY)) },
                        onAddPet = onAddPet,
                        onToggle = { task, done ->
                            viewModel.setCompleted(
                                task = task,
                                completed = done,
                                onDone = {
                                    feedback.show(
                                        if (done) "\"${task.name}\" completed"
                                        else "\"${task.name}\" marked not done"
                                    )
                                },
                                onError = { feedback.show(it) }
                            )
                        },
                        onSwipeDelete = { taskToDelete = it },
                        onReset = { if (!resetInProgress) showResetDialog = true },
                        showReset = hasCompletedToday
                    )
                    TaskTab.UPCOMING -> UpcomingList(items = s.data.upcoming, hasPets = s.data.hasPets, onOpenTask = onOpenTask, onCreateTask = { onCreateTask(createStartDay(TaskTab.UPCOMING)) })
                }
            }
        }
    }

    taskToDelete?.let { task ->
        ConfirmationDialog(
            title = "Delete task?",
            message = "\"${task.name}\" will be removed from the routine.",
            confirmLabel = "Delete",
            onConfirm = { taskToDelete = null; performDelete(task) },
            onDismiss = { taskToDelete = null }
        )
    }

    if (showResetDialog) {
        ConfirmationDialog(
            title = "Reset today's checklist?",
            message = "Completed tasks will become incomplete again.",
            confirmLabel = "Reset",
            onConfirm = {
                showResetDialog = false
                resetInProgress = true
                viewModel.resetToday { count ->
                    resetInProgress = false
                    feedback.show(if (count == 1) "1 task reset" else "$count tasks reset")
                }
            },
            onDismiss = { showResetDialog = false }
        )
    }
}

@Composable
private fun TodayList(
    data: TasksData,
    onOpenTask: (Long) -> Unit,
    onCreateTask: () -> Unit,
    onAddPet: () -> Unit,
    onToggle: (CareTask, Boolean) -> Unit,
    onSwipeDelete: (CareTask) -> Unit,
    onReset: () -> Unit,
    showReset: Boolean
) {
    when {
        !data.hasPets -> EmptyState(
            icon = Icons.Outlined.Pets,
            title = "Add a pet first",
            message = "Care tasks belong to a pet. Add one to get started.",
            actionLabel = "Add pet",
            onAction = onAddPet
        )
        data.todayGroups.isEmpty() -> EmptyState(
            icon = Icons.Outlined.TaskAlt,
            title = "Nothing scheduled today",
            message = "Create a daily or weekly routine and it will show up here.",
            actionLabel = "Create care task",
            onAction = onCreateTask
        )
        else -> LazyColumn(
            contentPadding = PaddingValues(start = Spacing.medium, end = Spacing.medium, bottom = 96.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                SectionHeader(
                    title = "Today's tasks",
                    actionLabel = if (showReset) "Reset" else null,
                    onAction = if (showReset) onReset else null
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.padding(top = Spacing.small, bottom = Spacing.small)
                ) {
                    Column(Modifier.padding(Spacing.medium)) {
                        Text("Pro-tip", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text("Use Reset above to clear today's checklist. Open a task to edit or delete it; swipes and shake are optional shortcuts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
            data.todayGroups.forEach { group ->
                item(key = "header-${group.pet.id}") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.large, bottom = Spacing.small)) {
                        PetAvatar(pet = group.pet, size = 28.dp)
                        Spacer(Modifier.width(Spacing.small))
                        Text(
                            group.pet.name.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${group.completedIds.size}/${group.tasks.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                items(group.tasks, key = { it.id }) { task ->
                    val done = task.id in group.completedIds
                    SwipeableTaskCard(
                        task = task,
                        completed = done,
                        overdue = task.id in group.overdueIds,
                        onToggle = { onToggle(task, !done) },
                        onClick = { onOpenTask(task.id) },
                        onSwipeComplete = { onToggle(task, !done) },
                        onSwipeDelete = { onSwipeDelete(task) },
                        modifier = Modifier.padding(bottom = Spacing.small)
                    )
                }
            }
        }
    }
}

@Composable
private fun UpcomingList(items: List<UpcomingItem>, hasPets: Boolean, onOpenTask: (Long) -> Unit, onCreateTask: () -> Unit) {
    if (items.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.EventAvailable,
            title = "No upcoming care",
            message = if (hasPets) "One-off, weekly and monthly tasks scheduled for later will appear here." else "Add a pet and plan their care to see it here.",
            actionLabel = if (hasPets) "Create care task" else null,
            onAction = if (hasPets) onCreateTask else null
        )
        return
    }
    val grouped = items.groupBy { DateTimeFormats.relative(it.date) }
    LazyColumn(
        contentPadding = PaddingValues(start = Spacing.medium, end = Spacing.medium, bottom = 96.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        grouped.forEach { (label, group) ->
            item(key = "h-$label") { SectionHeader(label) }
            items(group, key = { "${it.task.id}-${it.date}" }) { item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.small)
                        .clickable { onOpenTask(item.task.id) }
                ) {
                    Row(Modifier.padding(Spacing.medium), verticalAlignment = Alignment.CenterVertically) {
                        PetAvatar(pet = item.pet, size = 40.dp)
                        Spacer(Modifier.width(Spacing.medium))
                        Column(Modifier.weight(1f)) {
                            Text(item.task.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${item.pet.name} • ${item.task.categoryType.label} • ${DateTimeFormats.time(item.task.timeMinutes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            DateTimeFormats.shortDate(item.date),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** Today keeps the form's default; Upcoming starts the new task tomorrow. */
internal fun createStartDay(tab: TaskTab, today: java.time.LocalDate = java.time.LocalDate.now()): Long? =
    if (tab == TaskTab.UPCOMING) today.plusDays(1).toEpochDay() else null
