package com.petcare.app.ui.screens.delegation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petcare.app.model.subtitle
import com.petcare.app.ui.common.UiState
import com.petcare.app.ui.common.appViewModel
import com.petcare.app.ui.components.ErrorState
import com.petcare.app.ui.components.FormTextField
import com.petcare.app.ui.components.LoadingState
import com.petcare.app.ui.components.PetAvatar
import com.petcare.app.ui.components.PetCareTopBar
import com.petcare.app.ui.components.SectionHeader
import com.petcare.app.ui.theme.Spacing

/** Screen 20 — choose what to share and with whom. */
@Composable
fun DelegateScreen(onBack: () -> Unit, onContinue: (petId: Long, flags: String, phone: String) -> Unit) {
    val viewModel = appViewModel { c, handle -> DelegateViewModel(c.petRepository, handle) }
    val petState by viewModel.pet.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    var continueGuard by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { continueGuard = false }

    Scaffold(topBar = { PetCareTopBar(title = "Delegate care", onBack = onBack) }) { padding ->
        when (val s = petState) {
            UiState.Loading -> LoadingState(Modifier.padding(padding))
            is UiState.Error -> ErrorState(s.message, Modifier.padding(padding))
            is UiState.Success -> {
                val pet = s.data
                if (pet == null) {
                    ErrorState("This pet no longer exists.", Modifier.padding(padding))
                    return@Scaffold
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(horizontal = Spacing.medium)
                ) {
                    Spacer(Modifier.height(Spacing.small))
                    Card(shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Row(Modifier.padding(Spacing.medium), verticalAlignment = Alignment.CenterVertically) {
                        PetAvatar(pet = pet, size = 48.dp)
                        Spacer(Modifier.width(Spacing.medium))
                        Column {
                            Text(pet.name, style = MaterialTheme.typography.titleLarge)
                            Text(pet.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } }
                    Spacer(Modifier.height(Spacing.medium))
                    Text(
                        "Share ${pet.name}'s care plan with a friend, family member or sitter. You'll review the message before it opens in your messaging app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SectionHeader("What would you like to share?")
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(vertical = Spacing.xs)) {
                            OptionRow("Today's checklist", form.options.includeChecklist, enabled = !continueGuard) { v -> viewModel.setOptions { copy(includeChecklist = v) } }
                            OptionRow("Feeding schedule", form.options.includeFeeding, enabled = !continueGuard) { v -> viewModel.setOptions { copy(includeFeeding = v) } }
                            OptionRow("Medication reminders", form.options.includeMedication, enabled = !continueGuard) { v -> viewModel.setOptions { copy(includeMedication = v) } }
                            OptionRow("Care notes", form.options.includeNotes, enabled = !continueGuard) { v -> viewModel.setOptions { copy(includeNotes = v) } }
                        }
                    }
                    if (form.optionsError != null) {
                        Text(
                            form.optionsError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = Spacing.xs, start = Spacing.medium)
                        )
                    }

                    SectionHeader("Send to")
                    FormTextField(
                        value = form.phone,
                        onValueChange = viewModel::onPhoneChange,
                        label = "Phone number",
                        required = true,
                        error = form.phoneError,
                        placeholder = "+977 98XXXXXXXX",
                        keyboardType = KeyboardType.Phone,
                        leadingIcon = Icons.Outlined.Phone,
                        enabled = !continueGuard
                    )

                    Spacer(Modifier.height(Spacing.large))
                    Button(
                        onClick = {
                            if (!continueGuard) {
                                viewModel.validate()?.let { (flags, phone) ->
                                    continueGuard = true
                                    onContinue(viewModel.petId, flags, phone)
                                }
                            }
                        },
                        enabled = !continueGuard,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = MaterialTheme.shapes.large
                    ) { Text("Preview message") }
                    Spacer(Modifier.height(Spacing.xl))
                }
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = Spacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = if (enabled) onChange else null, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
