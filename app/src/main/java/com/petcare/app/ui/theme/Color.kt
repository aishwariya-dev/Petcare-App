package com.petcare.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.petcare.app.model.TaskCategory

// "Warm Companion" Material 3 palette: cream surfaces, deep amber primary,
// warm sand secondary, muted blue tertiary. Tokens mirror the design system.

// Light scheme
val md_light_primary = Color(0xFF7C5800)
val md_light_onPrimary = Color(0xFFFFFFFF)
val md_light_primaryContainer = Color(0xFFFFDEA7)
val md_light_onPrimaryContainer = Color(0xFF271900)
val md_light_secondary = Color(0xFF6B5D3F)
val md_light_onSecondary = Color(0xFFFFFFFF)
val md_light_secondaryContainer = Color(0xFFF2DDB7)
val md_light_onSecondaryContainer = Color(0xFF241A03)
val md_light_tertiary = Color(0xFF16487B)
val md_light_onTertiary = Color(0xFFFFFFFF)
val md_light_tertiaryContainer = Color(0xFFD3E3FF)
val md_light_onTertiaryContainer = Color(0xFF001C39)
val md_light_error = Color(0xFFBA1A1A)
val md_light_onError = Color(0xFFFFFFFF)
val md_light_errorContainer = Color(0xFFFFDAD6)
val md_light_onErrorContainer = Color(0xFF93000A)
val md_light_background = Color(0xFFFFF8F4)
val md_light_onBackground = Color(0xFF1F1B15)
val md_light_surface = Color(0xFFFFF8F4)
val md_light_onSurface = Color(0xFF1F1B15)
val md_light_surfaceVariant = Color(0xFFEAE1D7)
val md_light_onSurfaceVariant = Color(0xFF4E4537)
val md_light_outline = Color(0xFF85736B)
val md_light_outlineVariant = Color(0xFFD7C2B9)
val md_light_inverseSurface = Color(0xFF343029)
val md_light_inverseOnSurface = Color(0xFFF9EFE5)
val md_light_inversePrimary = Color(0xFFEFBF65)
val md_light_surfaceContainerLowest = Color(0xFFFFFFFF)
val md_light_surfaceContainerLow = Color(0xFFFDF2EB)
val md_light_surfaceContainer = Color(0xFFF7ECE4)
val md_light_surfaceContainerHigh = Color(0xFFF1E6DF)
val md_light_surfaceContainerHighest = Color(0xFFECE0D9)

// Dark scheme (same hues, inverted tonal steps)
val md_dark_primary = Color(0xFFEFBF65)
val md_dark_onPrimary = Color(0xFF412D00)
val md_dark_primaryContainer = Color(0xFF5E4200)
val md_dark_onPrimaryContainer = Color(0xFFFFDEA7)
val md_dark_secondary = Color(0xFFD8C4A0)
val md_dark_onSecondary = Color(0xFF3A2F15)
val md_dark_secondaryContainer = Color(0xFF534529)
val md_dark_onSecondaryContainer = Color(0xFFF5E0BA)
val md_dark_tertiary = Color(0xFFA3C9FF)
val md_dark_onTertiary = Color(0xFF00315C)
val md_dark_tertiaryContainer = Color(0xFF16487B)
val md_dark_onTertiaryContainer = Color(0xFFD3E3FF)
val md_dark_error = Color(0xFFFFB4AB)
val md_dark_onError = Color(0xFF690005)
val md_dark_errorContainer = Color(0xFF93000A)
val md_dark_onErrorContainer = Color(0xFFFFDAD6)
val md_dark_background = Color(0xFF17130E)
val md_dark_onBackground = Color(0xFFECE0D9)
val md_dark_surface = Color(0xFF17130E)
val md_dark_onSurface = Color(0xFFECE0D9)
val md_dark_surfaceVariant = Color(0xFF4E4537)
val md_dark_onSurfaceVariant = Color(0xFFD2C5B4)
val md_dark_outline = Color(0xFF9B8F80)
val md_dark_outlineVariant = Color(0xFF4E4537)
val md_dark_inverseSurface = Color(0xFFECE0D9)
val md_dark_inverseOnSurface = Color(0xFF343029)
val md_dark_inversePrimary = Color(0xFF7C5800)
val md_dark_surfaceContainerLowest = Color(0xFF110E09)
val md_dark_surfaceContainerLow = Color(0xFF1F1B15)
val md_dark_surfaceContainer = Color(0xFF241F19)
val md_dark_surfaceContainerHigh = Color(0xFF2E2923)
val md_dark_surfaceContainerHighest = Color(0xFF39342D)

// Gesture underlays — deliberately unambiguous, identical in both schemes.
val GestureCompleteBackground = Color(0xFF2E7D32)
val GestureDeleteBackground = Color(0xFFBA1A1A)
val GestureOnBackground = Color(0xFFFFFFFF)

/** Accent + container pair for a care category chip or badge. */
data class CategoryColors(val accent: Color, val container: Color, val onContainer: Color)

private val Feeding = CategoryColors(Color(0xFFD97706), Color(0xFFFFDDB3), Color(0xFF2A1800))
private val Exercise = CategoryColors(Color(0xFF15803D), Color(0xFFBCF0C3), Color(0xFF00210A))
private val Grooming = CategoryColors(Color(0xFF7C3AED), Color(0xFFEDE9FE), Color(0xFF2E0066))
private val Medication = CategoryColors(Color(0xFFE11D48), Color(0xFFFFE4E6), Color(0xFF4C0012))
private val Healthcare = CategoryColors(Color(0xFF0284C7), Color(0xFFE0F2FE), Color(0xFF001E30))
private val Neutral = CategoryColors(Color(0xFF6B5D3F), Color(0xFFF2DDB7), Color(0xFF241A03))

val TaskCategory.colors: CategoryColors
    get() = when (this) {
        TaskCategory.FEEDING -> Feeding
        TaskCategory.EXERCISE -> Exercise
        TaskCategory.GROOMING, TaskCategory.CLEANING -> Grooming
        TaskCategory.MEDICATION -> Medication
        TaskCategory.HEALTH_CHECK, TaskCategory.VACCINATION -> Healthcare
        TaskCategory.OTHER -> Neutral
    }

// Species accents used for avatar placeholders.
val SpeciesDog = Color(0xFFC97B2A)
val SpeciesCat = Color(0xFF7D6CC2)
val SpeciesBird = Color(0xFF3F8FD1)
val SpeciesRabbit = Color(0xFFC96A88)
val SpeciesFish = Color(0xFF2E9C93)
val SpeciesOther = Color(0xFF85736B)
