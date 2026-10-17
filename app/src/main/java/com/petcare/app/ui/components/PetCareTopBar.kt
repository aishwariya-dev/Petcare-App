package com.petcare.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** Shared top app bar. Root tabs receive the compact PetCare brand lockup. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetCareTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    profileName: String? = null,
    profilePhotoPath: String? = null,
    onProfile: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            if (onBack == null && profileName != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BrandLockup(title)
                    // Profile chip on the trailing side: photo (or initials) plus first name.
                    Row(
                        modifier = if (onProfile != null) {
                            Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable(onClickLabel = "Open profile", onClick = onProfile)
                                .padding(vertical = 4.dp, horizontal = 4.dp)
                        } else Modifier,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            firstName(profileName),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(8.dp))
                        UserAvatar(
                            name = profileName,
                            photoPath = profilePhotoPath,
                            size = 38.dp
                        )
                    }
                }
            } else if (onBack == null) {
                BrandLockup(title)
            } else Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
private fun BrandLockup(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.Pets,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(10.dp))
                .padding(8.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text("PetCare", style = MaterialTheme.typography.titleLarge)
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** "Nitesh Shrestha" → "Nitesh"; a blank name stays blank. */
fun firstName(fullName: String): String = fullName.trim().substringBefore(' ')
