package com.petcare.app.ui.screens.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petcare.app.ui.common.LocalFeedback
import com.petcare.app.ui.common.appViewModel
import com.petcare.app.ui.components.FormTextField
import com.petcare.app.ui.components.PetCareTopBar
import com.petcare.app.ui.theme.Spacing
import com.petcare.app.util.Validators

/** Screen 04 — create a new account. */
@Composable
fun RegisterScreen(onBack: () -> Unit, onRegistered: () -> Unit, onLogin: () -> Unit) {
    val viewModel = appViewModel { container, _ -> RegisterViewModel(container.authRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    val feedback = LocalFeedback.current
    LaunchedEffect(state.success) {
        if (state.success) {
            feedback.show(accountCreatedMessage(state.signedInName))
            onRegistered()
        }
    }
    val submitting = state.isSubmitting || state.success
    BackHandler(enabled = submitting) {}

    Scaffold(topBar = {
        PetCareTopBar(title = "Create account", onBack = { if (!submitting) onBack() })
    }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Spacing.medium)
                .padding(top = Spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(Icons.Outlined.Pets, null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(18.dp).size(38.dp))
            }
            Spacer(Modifier.height(Spacing.large))
            Text("Create your account", style = MaterialTheme.typography.headlineMedium)
            Text(
                "A calm home for every care detail.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs)
            )
            Spacer(Modifier.height(Spacing.large))

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
            FormTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = "Full name",
                required = true,
                error = state.nameError,
                enabled = !submitting
            )
            Spacer(Modifier.height(Spacing.small))
            FormTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = "Email",
                required = true,
                error = state.emailError,
                keyboardType = KeyboardType.Email,
                enabled = !submitting
            )
            }
            Spacer(Modifier.height(Spacing.small))
            FormTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = "Password",
                required = true,
                error = state.passwordError,
                keyboardType = KeyboardType.Password,
                password = true,
                supportingText = "At least ${Validators.MIN_PASSWORD_LENGTH} characters",
                enabled = !submitting
            )
            Spacer(Modifier.height(Spacing.small))
            FormTextField(
                value = state.confirmPassword,
                onValueChange = viewModel::onConfirmChange,
                label = "Confirm password",
                required = true,
                error = state.confirmError,
                keyboardType = KeyboardType.Password,
                password = true,
                enabled = !submitting
            )
            Spacer(Modifier.height(Spacing.small))
            FormTextField(
                value = state.phone,
                onValueChange = viewModel::onPhoneChange,
                label = "Phone number",
                placeholder = "e.g. +44 7700 900000",
                keyboardType = KeyboardType.Phone,
                supportingText = "Used to pre-fill SMS task delegation",
                enabled = !submitting
            )

            if (state.formError != null) {
                Spacer(Modifier.height(Spacing.small))
                Text(state.formError!!, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(Spacing.large))
            Button(
                onClick = {
                    viewModel.submit()
                },
                enabled = !submitting,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.large
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Creating account…")
                } else {
                    Text("Create account")
                }
            }

            Spacer(Modifier.height(Spacing.medium))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Already have an account?", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onLogin, enabled = !submitting) { Text("Log in") }
            }
            Spacer(Modifier.height(Spacing.large))
        }
    }
}
