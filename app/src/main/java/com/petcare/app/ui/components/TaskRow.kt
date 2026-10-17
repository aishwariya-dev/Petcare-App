package com.petcare.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Vaccines
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.petcare.app.model.TaskCategory
import com.petcare.app.ui.theme.GestureCompleteBackground
import com.petcare.app.ui.theme.GestureDeleteBackground
import com.petcare.app.ui.theme.GestureOnBackground
import com.petcare.app.ui.theme.colors
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.petcare.app.model.CareTask
import com.petcare.app.model.categoryType
import com.petcare.app.ui.theme.Spacing
import com.petcare.app.util.DateTimeFormats

/** Pill-shaped category tag coloured by care category (icon + label, never colour alone). */
@Composable
fun CategoryChip(category: TaskCategory, modifier: Modifier = Modifier) {
    val colors = category.colors
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(colors.container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = category.icon(),
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = category.label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onContainer
        )
    }
}

fun TaskCategory.icon(): ImageVector = when (this) {
    TaskCategory.FEEDING -> Icons.Outlined.Restaurant
    TaskCategory.EXERCISE -> Icons.AutoMirrored.Outlined.DirectionsWalk
    TaskCategory.MEDICATION -> Icons.Outlined.Medication
    TaskCategory.GROOMING -> Icons.Outlined.ContentCut
    TaskCategory.CLEANING -> Icons.Outlined.CleaningServices
    TaskCategory.HEALTH_CHECK -> Icons.Outlined.MedicalServices
    TaskCategory.VACCINATION -> Icons.Outlined.Vaccines
    TaskCategory.OTHER -> Icons.Outlined.Pets
}

/**
 * One task card body. Completion is shown with a filled green check, a "Done"
 * label, strikethrough and reduced emphasis — never colour alone.
 */
@Composable
fun TaskRow(
    task: CareTask,
    completed: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    overdue: Boolean = false,
    showCategory: Boolean = true,
    petName: String? = null
) {
    val contentAlpha = if (completed) 0.55f else 1f
    val stateLabel = if (completed) "completed" else "not completed"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(start = Spacing.small, end = Spacing.medium, top = Spacing.small, bottom = Spacing.small)
            .semantics { contentDescription = "${task.name}, $stateLabel" },
        verticalAlignment = Alignment.Top
    ) {
        IconButton(
            onClick = onToggle,
            modifier = Modifier
                .size(44.dp)
                .semantics { contentDescription = if (completed) "Mark as not done" else "Mark as done" }
        ) {
            if (completed) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(GestureCompleteBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = GestureOnBackground,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                )
            }
        }
        Spacer(Modifier.width(Spacing.xs))
        Column(modifier = Modifier.weight(1f)) {
            if (showCategory || petName != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    if (showCategory) CategoryChip(task.categoryType)
                    Spacer(Modifier.weight(1f))
                    if (petName != null) {
                        Icon(
                            Icons.Outlined.Pets, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            petName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            Text(
                text = task.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                textDecoration = if (completed) TextDecoration.LineThrough else null
            )
            if (task.notes.isNotBlank()) {
                Text(
                    text = task.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(Spacing.small))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Outlined.Schedule, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = DateTimeFormats.time(task.timeMinutes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                )
                Spacer(Modifier.weight(1f))
                when {
                    completed -> StatusTag("Done", GestureCompleteBackground, Icons.Filled.Check)
                    overdue -> StatusTag("Overdue", MaterialTheme.colorScheme.error, null)
                    else -> StatusTag("Upcoming", MaterialTheme.colorScheme.onSurfaceVariant, null)
                }
            }
        }
    }
}

@Composable
private fun StatusTag(label: String, color: Color, icon: ImageVector?) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (icon == null) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
            .padding(horizontal = if (icon == null) 10.dp else 0.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/**
 * Swipe right to toggle completion, and swipe left to request deletion. The
 * action label and icon are revealed behind the card while dragging, and the
 * card must travel at least half its width before the action fires (guards
 * against accidental swipes). The caller owns deletion confirmation and undo.
 */
@Composable
fun SwipeableTaskCard(
    task: CareTask,
    completed: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onSwipeComplete: () -> Unit,
    onSwipeDelete: () -> Unit,
    modifier: Modifier = Modifier,
    overdue: Boolean = false,
    petName: String? = null
) {
    val currentOnComplete by rememberUpdatedState(onSwipeComplete)
    val currentOnDelete by rememberUpdatedState(onSwipeDelete)

    key(task.id) {
        val dismissState = rememberSwipeToDismissBoxState(
            positionalThreshold = { totalDistance -> totalDistance * 0.5f }
        )

        LaunchedEffect(dismissState.currentValue) {
            when (dismissState.currentValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    currentOnComplete()
                    dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    currentOnDelete()
                    dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                }
                SwipeToDismissBoxValue.Settled -> Unit
            }
        }

        SwipeToDismissBox(
            state = dismissState,
            modifier = modifier.clip(MaterialTheme.shapes.medium),
            backgroundContent = {
                val direction = dismissState.dismissDirection
                val targetColor = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> GestureCompleteBackground
                    SwipeToDismissBoxValue.EndToStart -> GestureDeleteBackground
                    SwipeToDismissBoxValue.Settled -> Color.Transparent
                }
                val color by animateColorAsState(targetColor, label = "swipeBackground")
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color)
                        .padding(horizontal = Spacing.large),
                    contentAlignment = when (direction) {
                        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                        else -> Alignment.CenterStart
                    }
                ) {
                    when (direction) {
                        SwipeToDismissBoxValue.StartToEnd -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, null, tint = GestureOnBackground)
                            Spacer(Modifier.width(Spacing.small))
                            Text(
                                if (completed) "Mark as not done" else "Mark as done",
                                style = MaterialTheme.typography.labelLarge,
                                color = GestureOnBackground
                            )
                        }
                        SwipeToDismissBoxValue.EndToStart -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Delete task",
                                style = MaterialTheme.typography.labelLarge,
                                color = GestureOnBackground
                            )
                            Spacer(Modifier.width(Spacing.small))
                            Icon(Icons.Outlined.Delete, null, tint = GestureOnBackground)
                        }
                        SwipeToDismissBoxValue.Settled -> Unit
                    }
                }
            }
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (completed) MaterialTheme.colorScheme.surfaceContainerLow
                    else MaterialTheme.colorScheme.surfaceContainerLowest
                ),
                border = if (completed) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = MaterialTheme.shapes.medium
            ) {
                TaskRow(
                    task = task,
                    completed = completed,
                    onToggle = onToggle,
                    onClick = onClick,
                    overdue = overdue,
                    petName = petName,
                    modifier = Modifier.padding(vertical = Spacing.xs)
                )
            }
        }
    }
}

/** Space-efficient row used inside a pet's card on the dashboard. */
@Composable
fun CompactTaskRow(
    task: CareTask,
    completed: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            IconButton(onClick = onToggle, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (completed) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = if (completed) "Mark as not done" else "Mark as done",
                    tint = if (completed) GestureCompleteBackground else MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text = task.name,
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = if (completed) TextDecoration.LineThrough else null,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (completed) 0.6f else 1f)
            )
        }
        Text(
            text = if (completed) "Done" else DateTimeFormats.time(task.timeMinutes),
            style = MaterialTheme.typography.labelMedium,
            color = if (completed) GestureCompleteBackground else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
