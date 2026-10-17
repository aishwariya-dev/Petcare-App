package com.petcare.app.ui.screens.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material3.Icon
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import com.petcare.app.data.local.DatabaseSeeder
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

/** Screen 03. */
@Composable
fun LoginScreen(onBack: () -> Unit, onLoggedIn: () -> Unit, onRegister: () -> Unit) {
    val viewModel = appViewModel { container, _ -> LoginViewModel(container.authRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    val feedback = LocalFeedback.current
    LaunchedEffect(state.success) {
        if (state.success) {
            feedback.show(welcomeBackMessage(state.signedInName))
            onLoggedIn()
        }
    }
    val submitting = state.isSubmitting || state.success
    BackHandler(enabled = submitting) {}

    Scaffold(topBar = {
        PetCareTopBar(title = "Log in", onBack = { if (!submitting) onBack() })
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
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(Icons.Outlined.Pets, null, tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(18.dp).size(38.dp))
            }
            Spacer(Modifier.height(Spacing.large))
            Text("Welcome to PetCare", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Log in to see your household's care plan.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs)
            )
            Spacer(Modifier.height(Spacing.large))

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                FormTextField(
                    value = state.email,
                    onValueChange = viewModel::onEmailChange,
                    label = "Email",
                    required = true,
                    error = state.emailError,
                    keyboardType = KeyboardType.Email,
                    enabled = !submitting,
                    placeholder = "you@example.com"
                )
                Spacer(Modifier.height(Spacing.small))
                FormTextField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = "Password",
                    required = true,
                    error = state.passwordError,
                    keyboardType = KeyboardType.Password,
                    password = true,
                    enabled = !submitting
                )
            }

            if (state.formError != null) {
                Spacer(Modifier.height(Spacing.small))
                Text(
                    state.formError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
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
                    Text("Signing in…")
                } else {
                    Icon(Icons.AutoMirrored.Outlined.Login, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Log in")
                }
            }

            Spacer(Modifier.height(Spacing.medium))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("New to PetCare?", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRegister, enabled = !submitting) { Text("Create account") }
            }

            Spacer(Modifier.height(Spacing.small))
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Demo household", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(
                            "Emily, with Max and Luna already set up.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    TextButton(onClick = {
                        viewModel.onEmailChange(DatabaseSeeder.DEMO_EMAIL)
                        viewModel.onPasswordChange(DatabaseSeeder.DEMO_PASSWORD)
                    }, enabled = !submitting) { Text("Fill in") }
                }
            }
            Spacer(Modifier.height(Spacing.large))
        }
    }
}
