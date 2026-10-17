package com.petcare.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Vaccines
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.petcare.app.ui.theme.Spacing

/** Screen 02. Value proposition + two clear actions. */
@Composable
fun WelcomeScreen(onGetStarted: () -> Unit, onLogin: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.large, vertical = Spacing.xl),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Pets,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(Modifier.height(Spacing.large))
            Text("Welcome to PetCare", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(Spacing.small))
            Text(
                "Keep every pet's routine, health and care in one place.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.xl))
            FeatureRow(Icons.Outlined.Checklist, "Daily and weekly care routines")
            Spacer(Modifier.height(Spacing.medium))
            FeatureRow(Icons.Outlined.Vaccines, "Medication and vaccination reminders")
            Spacer(Modifier.height(Spacing.medium))
            FeatureRow(Icons.Outlined.Share, "Share care plans by SMS when you're away")
        }

        Column {
            Button(onClick = onGetStarted, modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.large) {
                Text("Get started")
            }
            Spacer(Modifier.height(Spacing.small))
            OutlinedButton(onClick = onLogin, modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.large) {
                Text("Log in")
            }
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Spacing.medium))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
