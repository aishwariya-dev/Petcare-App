package com.petcare.app.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petcare.app.ui.common.LocalFeedback
import com.petcare.app.ui.common.appViewModel
import com.petcare.app.ui.components.FormTextField
import com.petcare.app.ui.components.ImagePickerField
import com.petcare.app.ui.components.LoadingState
import com.petcare.app.ui.components.PetCareTopBar
import com.petcare.app.ui.components.SectionHeader
import com.petcare.app.ui.components.UserAvatar
import com.petcare.app.ui.theme.Spacing
import com.petcare.app.util.Validators
import com.petcare.app.util.DateTimeFormats
import java.time.Instant
import java.time.ZoneId

/** Profile — reached from Settings. Name and profile photo are editable. */
@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { c, _ -> ProfileViewModel(c.authRepository, c.imageStorage) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val password by viewModel.password.collectAsStateWithLifecycle()
    val feedback = LocalFeedback.current

    BackHandler {
        if (!state.isSaving) {
            viewModel.discardPendingPhoto()
            onBack()
        }
    }

    Scaffold(
        topBar = {
            PetCareTopBar(
                title = "Profile",
                onBack = {
                    if (!state.isSaving) {
                        viewModel.discardPendingPhoto()
                        onBack()
                    }
                }
            )
        }
    ) { padding ->
        val user = state.user
        if (!state.loaded || user == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(Spacing.large))

            // ── Profile photo ────────────────────────────────────────────────────
            ImagePickerField(
                imagePath = state.visiblePhotoPath,
                pendingUri = state.pendingPhotoUri,
                onImagePicked = viewModel::onPhotoPicked,
                onRemove = viewModel::onPhotoRemoved,
                    enabled = !state.isSaving,
                label = "Profile photo",
                    onPickerError = feedback::show,
                    placeholder = {
                        UserAvatar(
                            name = user.name,
                            photoPath = null,
                            size = 112.dp
                        )
                    }
            )

            Spacer(Modifier.height(Spacing.small))
            Text(
                "Member since ${DateTimeFormats.longDate(
                    Instant.ofEpochMilli(user.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
                )}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SectionHeader("Details")
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(Spacing.medium)) {
                    FormTextField(
                        state.name, viewModel::onNameChange, "Name",
                        required = true, error = state.nameError, enabled = !state.isSaving
                    )
                    Spacer(Modifier.height(Spacing.small))
                    FormTextField(
                        value = user.email,
                        onValueChange = {},
                        label = "Email",
                        enabled = false,
                        supportingText = "Your email is used to log in and can't be changed."
                    )
                    Spacer(Modifier.height(Spacing.small))
                    FormTextField(
                        value = state.phone,
                        onValueChange = viewModel::onPhoneChange,
                        label = "Phone number",
                        error = state.phoneError,
                        keyboardType = KeyboardType.Phone,
                        supportingText = "Shown when delegating care tasks via SMS.",
                        enabled = !state.isSaving
                    )
                }
            }

            Spacer(Modifier.height(Spacing.large))
            Button(
                onClick = {
                    viewModel.save(
                        onSaved = { feedback.show("Profile updated"); onBack() },
                        onError = { feedback.show(it) }
                    )
                },
                enabled = state.dirty && !state.isSaving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.large
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Save changes")
                }
            }

            Spacer(Modifier.height(Spacing.large))
            SectionHeader("Change password")
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(Spacing.medium)) {
                    FormTextField(
                        value = password.current,
                        onValueChange = viewModel::onCurrentPasswordChange,
                        label = "Current password",
                        error = password.currentError,
                        keyboardType = KeyboardType.Password,
                        password = true,
                        enabled = !password.isSaving
                    )
                    Spacer(Modifier.height(Spacing.small))
                    FormTextField(
                        value = password.new,
                        onValueChange = viewModel::onNewPasswordChange,
                        label = "New password",
                        error = password.newError,
                        keyboardType = KeyboardType.Password,
                        password = true,
                        supportingText = "At least ${Validators.MIN_PASSWORD_LENGTH} characters.",
                        enabled = !password.isSaving
                    )
                    Spacer(Modifier.height(Spacing.small))
                    FormTextField(
                        value = password.confirm,
                        onValueChange = viewModel::onConfirmPasswordChange,
                        label = "Confirm new password",
                        error = password.confirmError,
                        keyboardType = KeyboardType.Password,
                        password = true,
                        enabled = !password.isSaving
                    )
                    Spacer(Modifier.height(Spacing.medium))
                    OutlinedButton(
                        onClick = { viewModel.changePassword { feedback.show("Password updated") } },
                        enabled = password.canSubmit,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (password.isSaving) "Updating…" else "Update password") }
                }
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}
