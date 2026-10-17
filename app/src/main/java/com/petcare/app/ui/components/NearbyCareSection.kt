package com.petcare.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.petcare.app.ui.theme.Spacing
import com.petcare.app.util.NearbyCareType

/**
 * Simple external search shortcuts. Results are intentionally supplied by the
 * user's Maps/browser app rather than embedded or persisted by PetCare.
 */
@Composable
fun NearbyCareSection(
    onOpenSearch: (NearbyCareType) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(Spacing.medium)) {
            Text("Find nearby care", style = MaterialTheme.typography.titleMedium)
            Text(
                "Search your Maps app or browser for local pet care.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(
                modifier = Modifier.padding(top = Spacing.small),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                NearbyCareType.entries.forEach { type ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (type == NearbyCareType.VETERINARIANS) {
                                    Icons.Outlined.MedicalServices
                                } else {
                                    Icons.Outlined.Search
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                type.label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = Spacing.small)
                            )
                        }
                        TextButton(
                            onClick = { onOpenSearch(type) },
                        ) {
                            Text("Search")
                        }
                    }
                }
            }
        }
    }
}