package com.petcare.app.ui.screens.delegation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petcare.app.ui.common.LocalFeedback
import com.petcare.app.ui.common.UiState
import com.petcare.app.ui.common.appViewModel
import com.petcare.app.ui.components.ErrorState
import com.petcare.app.ui.components.LoadingState
import com.petcare.app.ui.components.PetCareTopBar
import com.petcare.app.ui.components.SectionHeader
import com.petcare.app.ui.theme.Spacing
import com.petcare.app.util.ShareLauncher
import com.petcare.app.util.SmsLauncher

/** Screen 21 — review, then hand off to the device's messaging app. */
@Composable
fun SmsPreviewScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val viewModel = appViewModel { c, handle -> SmsPreviewViewModel(c.petRepository, c.taskRepository, handle) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val feedback = LocalFeedback.current
    var openGuard by remember { mutableStateOf(false) }

    Scaffold(topBar = { PetCareTopBar(title = "Review message", onBack = onBack) }) { padding ->
        when (val s = state) {
            UiState.Loading -> LoadingState(Modifier.padding(padding))
            is UiState.Error -> ErrorState(s.message, Modifier.padding(padding))
            is UiState.Success -> {
                val data = s.data
                if (data == null) {
                    ErrorState("This pet no longer exists.", Modifier.padding(padding))
                    return@Scaffold
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.medium)
                ) {
                    SectionHeader("Message preview")
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.padding(Spacing.medium)) {
                            Text("To: ${data.phone}", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(Spacing.small))
                            Text(data.message, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                            Spacer(Modifier.height(Spacing.small))
                            Text("${data.message.length} characters", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(Modifier.height(Spacing.large))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.padding(Spacing.medium), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(Spacing.small))
                        Text(
                            "PetCare opens your messaging app with this text filled in. Nothing is sent until you tap send there.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    } }

                    Spacer(Modifier.height(Spacing.large))
                    Button(
                        onClick = {
                            if (!openGuard) {
                                openGuard = true
                                val opened = SmsLauncher.open(context, data.phone, data.message)
                                if (opened) {
                                    feedback.show("Opened Messages — review and send from there")
                                    onDone()
                                } else {
                                    openGuard = false
                                    feedback.show("No messaging app is available on this device")
                                }
                            }
                        },
                        enabled = !openGuard,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null)
                        Spacer(Modifier.width(Spacing.small))
                        Text("Open Messages")
                    }
                    Spacer(Modifier.height(Spacing.small))
                    OutlinedButton(
                        onClick = {
                            if (!ShareLauncher.shareText(context, data.message, "Share care instructions")) {
                                feedback.show("No app is available to share this text")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null)
                        Spacer(Modifier.width(Spacing.small))
                        Text("Share")
                    }
                    Spacer(Modifier.height(Spacing.xl))
                }
            }
        }
    }
}
