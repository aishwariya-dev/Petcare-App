package com.petcare.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.petcare.app.model.Pet
import com.petcare.app.model.User
import com.petcare.app.model.subtitle
import com.petcare.app.ui.theme.Spacing

/** A local account summary. This component only presents data supplied by its caller. */
@Composable
fun UserProfileSummaryCard(
    user: User,
    petCount: Int,
    onViewProfile: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = user.name
) {
    SummaryCard(
        onClick = onViewProfile,
        contentDescription = "View ${user.name}'s profile",
        modifier = modifier
    ) {
        UserAvatar(name = user.name, photoPath = user.photoPath, size = 52.dp)
        Spacer(Modifier.width(Spacing.medium))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = user.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (petCount == 1) "1 pet" else "$petCount pets",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "View profile",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** A local pet summary with today's progress. */
@Composable
fun PetSummaryCard(
    pet: Pet,
    completedToday: Int,
    totalToday: Int,
    onViewProfile: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    SummaryCard(
        onClick = onViewProfile,
        contentDescription = "View ${pet.name}'s profile",
        modifier = modifier
    ) {
        PetAvatar(pet = pet, size = if (compact) 48.dp else 64.dp)
        Spacer(Modifier.width(Spacing.medium))
        Column(Modifier.weight(1f)) {
            Text(
                text = pet.name,
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = pet.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (totalToday == 0) "No tasks today"
                else "$completedToday/$totalToday tasks done today",
                style = MaterialTheme.typography.labelMedium,
                color = if (totalToday > 0 && completedToday == totalToday) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SummaryCard(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}