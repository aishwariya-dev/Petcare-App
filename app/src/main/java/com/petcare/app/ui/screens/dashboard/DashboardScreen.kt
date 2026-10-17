package com.petcare.app.ui.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petcare.app.model.UpcomingItem
import com.petcare.app.model.subtitle
import com.petcare.app.ui.common.LocalFeedback
import com.petcare.app.ui.common.UiState
import com.petcare.app.ui.common.appViewModel
import com.petcare.app.ui.components.CompactTaskRow
import com.petcare.app.ui.components.EmptyState
import com.petcare.app.ui.components.ErrorState
import com.petcare.app.ui.components.LoadingState
import com.petcare.app.ui.components.PetAvatar
import com.petcare.app.ui.components.PetCareTopBar
import com.petcare.app.ui.components.PetSummaryCard
import com.petcare.app.ui.components.SectionHeader
import com.petcare.app.ui.theme.Spacing
import com.petcare.app.util.DateTimeFormats

/** Screen 05 — "What do I need to do for my pets today?" */
@Composable
fun DashboardScreen(
    onOpenTask: (Long) -> Unit,
    onOpenPet: (Long) -> Unit,
    onCreateTask: () -> Unit,
    onAddPet: () -> Unit,
    onOpenUpcoming: () -> Unit,
    onOpenExpenses: () -> Unit,
    onOpenNearbyCare: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val viewModel = appViewModel { c, _ ->
        DashboardViewModel(c.authRepository, c.petRepository, c.taskRepository)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val authenticatedUser by viewModel.authenticatedUser.collectAsStateWithLifecycle()
    val feedback = LocalFeedback.current

    Scaffold(
        topBar = {
            PetCareTopBar(
                title = "Home",
                profileName = authenticatedUser?.name,
                profilePhotoPath = authenticatedUser?.photoPath,
                onProfile = onOpenProfile
            )
        },
        floatingActionButton = {
            val data = (state as? UiState.Success)?.data
            if (data != null && data.hasPets && data.petsToday.isNotEmpty()) {
                FloatingActionButton(onClick = onCreateTask) {
                    Icon(Icons.Filled.Add, contentDescription = "Create care task")
                }
            }
        }
    ) { padding ->
        when (val s = state) {
            UiState.Loading -> LoadingState(Modifier.padding(padding))
            is UiState.Error -> ErrorState(s.message, Modifier.padding(padding))
            is UiState.Success -> DashboardContent(
                data = s.data,
                modifier = Modifier.padding(padding),
                onOpenTask = onOpenTask,
                onOpenPet = onOpenPet,
                onCreateTask = onCreateTask,
                onAddPet = onAddPet,
                onOpenUpcoming = onOpenUpcoming,
                onOpenExpenses = onOpenExpenses,
                onOpenNearbyCare = onOpenNearbyCare,
                onToggle = { task, completed ->
                    viewModel.toggleTask(
                        task = task,
                        completed = completed,
                        onDone = {
                            feedback.show(
                                if (completed) "Marked \"${task.name}\" as done"
                                else "Marked \"${task.name}\" as not done"
                            )
                        },
                        onError = { feedback.show(it) }
                    )
                }
            )
        }
    }
}

@Composable
private fun DashboardContent(
    data: DashboardData,
    modifier: Modifier,
    onOpenTask: (Long) -> Unit,
    onOpenPet: (Long) -> Unit,
    onCreateTask: () -> Unit,
    onAddPet: () -> Unit,
    onOpenUpcoming: () -> Unit,
    onOpenExpenses: () -> Unit,
    onOpenNearbyCare: () -> Unit,
    onToggle: (com.petcare.app.model.CareTask, Boolean) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.medium, end = Spacing.medium, bottom = 96.dp)
    ) {
        // ── Greeting + today at a glance ───────────────────────────────────────
        item {
            Spacer(Modifier.height(Spacing.small))
            Text(greeting(data.user.name), style = MaterialTheme.typography.headlineSmall)
            Text(
                if (data.totalToday == 0) "Nothing scheduled for today."
                else "Here's today's care plan.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (data.totalToday > 0) {
                Spacer(Modifier.height(Spacing.medium))
                TodayCard(progress = TodayProgress.from(data.petsToday))
            }
        }

        // ── My Pets row ────────────────────────────────────────────────────────
        if (data.allPets.isNotEmpty()) {
            item {
                SectionHeader("My Pets")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                    contentPadding = PaddingValues(bottom = Spacing.small)
                ) {
                    items(data.allPets, key = { it.id }) { pet ->
                        val today = data.petsToday.firstOrNull { it.pet.id == pet.id }
                        PetSummaryCard(
                            pet = pet,
                            completedToday = today?.completedIds?.size ?: 0,
                            totalToday = today?.tasks?.size ?: 0,
                            onViewProfile = { onOpenPet(pet.id) },
                            modifier = Modifier.width(240.dp),
                            compact = true
                        )
                    }
                }
            }
        }

        // ── Quick Actions ──────────────────────────────────────────────────────
        item {
            SectionHeader("Quick Actions")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                QuickActionCard(
                    "Add pet",
                    Icons.Outlined.Pets,
                    onAddPet,
                    Modifier.weight(1f)
                )
                QuickActionCard("Upcoming care", Icons.Outlined.Checklist, onOpenUpcoming, Modifier.weight(1f))
                QuickActionCard(
                    "Add task",
                    Icons.AutoMirrored.Outlined.PlaylistAdd,
                    onCreateTask,
                    Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(Spacing.small))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                QuickActionCard("Expenses", Icons.Outlined.AttachMoney, onOpenExpenses, Modifier.weight(1f))
                QuickActionCard("Nearby care", Icons.Outlined.LocationOn, onOpenNearbyCare, Modifier.weight(1f))
            }
            Spacer(Modifier.height(Spacing.small))
        }

        // ── Today's tasks by pet ────────────────────────────────────────────────
        when {
            !data.hasPets -> item {
                EmptyState(
                    icon = Icons.Outlined.Pets,
                    title = "Add your first pet",
                    message = "Create a profile for each pet, then build their daily care routine.",
                    actionLabel = "Add pet",
                    onAction = onAddPet
                )
            }
            data.petsToday.isEmpty() -> item {
                SectionHeader("Today's Tasks")
                EmptyState(
                    icon = Icons.Outlined.TaskAlt,
                    title = "No care tasks today",
                    message = "Add feeding, walks, medication or grooming to build a routine.",
                    actionLabel = "Create care task",
                    onAction = onCreateTask
                )
            }
            else -> {
                item { SectionHeader("Today's Tasks") }
                items(data.petsToday, key = { it.pet.id }) { petToday ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.medium)
                    ) {
                        Column(modifier = Modifier.padding(Spacing.medium)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable { onOpenPet(petToday.pet.id) }
                            ) {
                                PetAvatar(pet = petToday.pet, size = 52.dp)
                                Spacer(Modifier.width(Spacing.medium))
                                Column(Modifier.weight(1f)) {
                                    Text(petToday.pet.name, style = MaterialTheme.typography.titleLarge)
                                    Text(
                                        petToday.pet.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    "${petToday.completedIds.size}/${petToday.tasks.size}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            HorizontalDivider(Modifier.padding(vertical = Spacing.small))
                            petToday.tasks.forEach { task ->
                                val done = task.id in petToday.completedIds
                                CompactTaskRow(
                                    task = task,
                                    completed = done,
                                    onToggle = { onToggle(task, !done) },
                                    onClick = { onOpenTask(task.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Upcoming ────────────────────────────────────────────────────────────
        if (data.hasPets) {
            item {
                SectionHeader("Upcoming", actionLabel = "See all", onAction = onOpenUpcoming)
                if (data.upcoming.isEmpty()) {
                    Text(
                        "No upcoming one-off or weekly care in the next two weeks.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(data.upcoming, key = { "up-${it.task.id}-${it.date}" }) { item ->
                UpcomingRow(item = item, onClick = { onOpenTask(item.task.id) })
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = Spacing.small)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon, contentDescription = label,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center
            )
        }
    }
}


@Composable
private fun UpcomingRow(item: UpcomingItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PetAvatar(pet = item.pet, size = 36.dp)
        Spacer(Modifier.width(Spacing.medium))
        Column(Modifier.weight(1f)) {
            Text("${item.task.name} • ${item.pet.name}", style = MaterialTheme.typography.bodyLarge)
            Text(
                "${DateTimeFormats.relative(item.date)} • ${DateTimeFormats.time(item.task.timeMinutes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
