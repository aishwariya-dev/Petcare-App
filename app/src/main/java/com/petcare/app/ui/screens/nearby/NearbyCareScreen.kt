package com.petcare.app.ui.screens.nearby

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.petcare.app.ui.common.LocalFeedback
import com.petcare.app.ui.components.NearbyCareSection
import com.petcare.app.ui.components.PetCareTopBar
import com.petcare.app.ui.theme.Spacing
import com.petcare.app.util.NearbyCareLauncher

/** Vet, pharmacy and animal-hospital searches, opened in the phone's Maps app or browser. */
@Composable
fun NearbyCareScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val feedback = LocalFeedback.current
    Scaffold(topBar = { PetCareTopBar(title = "Nearby care", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.medium)
        ) {
            NearbyCareSection(
                onOpenSearch = { type ->
                    NearbyCareLauncher.open(context, type) { message -> feedback.show(message) }
                }
            )
        }
    }
}
