package com.petcare.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.petcare.app.BuildConfig
import com.petcare.app.ui.components.PetCareTopBar
import com.petcare.app.ui.components.SectionHeader
import com.petcare.app.ui.theme.Spacing

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(topBar = { PetCareTopBar(title = "About PetCare", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(Spacing.xl))
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Pets, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(Spacing.medium))
            Text("PetCare", style = MaterialTheme.typography.headlineMedium)
             Text(
                 "Version ${BuildConfig.VERSION_NAME}",
                 style = MaterialTheme.typography.bodyMedium,
                 color = MaterialTheme.colorScheme.onSurfaceVariant
             )
            Spacer(Modifier.height(Spacing.medium))
            Card(shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Text(
                "A simple companion for keeping every pet's daily care on track — feeding, walks, medication and vet visits — and for handing that routine to someone you trust when you're away.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(Spacing.medium)
            ) }

            SectionHeader("What it does")
            Feature("Pet profiles with diet, allergies and vaccination history")
            Feature("Daily and weekly care routines with a today checklist")
             Feature("Swipe to toggle completion; swipe left to delete after confirmation; shake to reset today's checklist")
            Feature("Delegate care by SMS with a message you review first")

            SectionHeader("Privacy")
            Text(
                "All data stays on this device. PetCare does not use an internet connection, cloud sync or analytics. Care delegation opens your messaging app with text for you to review and send. Automatic task SMS is separate, off by default, and can submit a scheduled message only after per-task consent, charge acknowledgement, Android permission and a default SMS SIM.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SectionHeader("Credits")
            Text(
                "Built with Kotlin, Jetpack Compose, Room and Material 3 as a university mobile application project.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun Feature(text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.small))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
