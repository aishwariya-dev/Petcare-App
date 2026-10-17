package com.petcare.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CrueltyFree
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.SetMeal
import androidx.compose.material.icons.outlined.FlutterDash
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.petcare.app.model.Pet
import com.petcare.app.model.Species
import com.petcare.app.model.speciesType
import com.petcare.app.ui.theme.SpeciesBird
import com.petcare.app.ui.theme.SpeciesCat
import com.petcare.app.ui.theme.SpeciesDog
import com.petcare.app.ui.theme.SpeciesFish
import com.petcare.app.ui.theme.SpeciesOther
import com.petcare.app.ui.theme.SpeciesRabbit
import java.io.File

fun Species.accentColor(): Color = when (this) {
    Species.DOG -> SpeciesDog
    Species.CAT -> SpeciesCat
    Species.BIRD -> SpeciesBird
    Species.RABBIT -> SpeciesRabbit
    Species.FISH -> SpeciesFish
    Species.OTHER -> SpeciesOther
}

fun Species.icon(): ImageVector = when (this) {
    Species.DOG, Species.CAT, Species.OTHER -> Icons.Outlined.Pets
    Species.BIRD -> Icons.Outlined.FlutterDash
    Species.RABBIT -> Icons.Outlined.CrueltyFree
    Species.FISH -> Icons.Outlined.SetMeal
}

/** Circular photo, or a species icon on a tinted disc when there is no photo. */
@Composable
fun PetAvatar(
    pet: Pet,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val species = pet.speciesType
    val shapeModifier = modifier.size(size).clip(CircleShape)
    val photo = pet.photoPath?.let { File(it) }?.takeIf { it.exists() }

    if (photo != null) {
        AsyncImage(
            model = photo,
            contentDescription = "${pet.name}'s photo",
            contentScale = ContentScale.Crop,
            modifier = shapeModifier
        )
    } else {
        Box(
            modifier = shapeModifier.background(species.accentColor().copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = species.icon(),
                contentDescription = "${species.label} icon",
                tint = species.accentColor(),
                modifier = Modifier.size(size * 0.5f)
            )
        }
    }
}

@Composable
fun PetAvatarPlaceholder(size: Dp = 48.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Pets,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

fun userInitials(name: String): String = name.trim()
    .split(Regex("\\s+"))
    .filter { it.isNotEmpty() }
    .take(2)
    .joinToString("") { it.first().uppercase() }
    .ifEmpty { "?" }

/**
 * The authenticated user's avatar. A missing, inaccessible, or unreadable
 * image deliberately falls back to initials so identity remains available
 * without depending on a network image loader.
 */
@Composable
fun UserAvatar(
    name: String,
    photoPath: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val photo = remember(photoPath) {
        photoPath?.let(::File)?.takeIf { it.isFile && it.canRead() }
    }
    var imageFailed by remember(photo) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (photo != null && !imageFailed) {
            AsyncImage(
                model = photo,
                contentDescription = "$name's profile photo",
                contentScale = ContentScale.Crop,
                onError = { imageFailed = true },
                modifier = Modifier.matchParentSize()
            )
        } else {
            Text(
                text = userInitials(name),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
