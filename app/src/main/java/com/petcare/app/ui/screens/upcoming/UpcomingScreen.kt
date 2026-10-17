package com.petcare.app.ui.screens.upcoming

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petcare.app.model.UpcomingItem
import com.petcare.app.model.categoryType
import com.petcare.app.ui.common.UiState
import com.petcare.app.ui.common.appViewModel
import com.petcare.app.ui.components.EmptyState
import com.petcare.app.ui.components.ErrorState
import com.petcare.app.ui.components.LoadingState
import com.petcare.app.ui.components.PetAvatar
import com.petcare.app.ui.components.PetCareTopBar
import com.petcare.app.ui.components.SectionHeader
import com.petcare.app.ui.theme.Spacing
import com.petcare.app.util.DateTimeFormats

/** Screen 22 — upcoming care grouped into This week / Next week / Later. */
@Composable
fun UpcomingScreen(onBack: () -> Unit, onOpenTask: (Long) -> Unit) {
    val viewModel = appViewModel { c, _ -> UpcomingViewModel(c.authRepository, c.petRepository, c.taskRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { PetCareTopBar(title = "Upcoming", onBack = onBack) }) { padding ->
        when (val s = state) {
            UiState.Loading -> LoadingState(Modifier.padding(padding))
            is UiState.Error -> ErrorState(s.message, Modifier.padding(padding))
            is UiState.Success -> if (s.data.items.isEmpty()) {
                Column(Modifier.padding(padding).fillMaxSize()) {
                    EmptyState(
                        icon = Icons.Outlined.EventAvailable,
                        title = "Nothing coming up",
                        message = "One-off appointments and weekly routines will appear here once scheduled."
                    )
                }
            } else {
                val sections = s.data.items.groupBy { item ->
                    when {
                        item.date.isBefore(s.data.today.plusDays(7)) -> "This week"
                        item.date.isBefore(s.data.today.plusDays(14)) -> "Next week"
                        else -> "Later"
                    }
                }
                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(start = Spacing.medium, end = Spacing.medium, bottom = Spacing.xl)
                ) {
                    listOf("This week", "Next week", "Later").forEach { label ->
                        val group = sections[label] ?: return@forEach
                        item(key = "h-$label") { SectionHeader(label) }
                        items(group, key = { "${it.task.id}-${it.date}" }) { item ->
                            UpcomingCard(item, onClick = { onOpenTask(item.task.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpcomingCard(item: UpcomingItem, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.small)
            .clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(Spacing.medium), verticalAlignment = Alignment.CenterVertically) {
            PetAvatar(pet = item.pet, size = 44.dp)
            Spacer(Modifier.width(Spacing.medium))
            Column(Modifier.weight(1f)) {
                Text(item.pet.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(item.task.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${DateTimeFormats.relative(item.date)} • ${DateTimeFormats.time(item.task.timeMinutes)} • ${item.task.categoryType.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                DateTimeFormats.shortDate(item.date),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
