package com.petcare.app.ui.screens.expenses

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petcare.app.model.ExpenseCategory
import com.petcare.app.ui.common.appViewModel
import com.petcare.app.ui.components.DateField
import com.petcare.app.ui.components.DropdownField
import com.petcare.app.ui.components.FormTextField
import com.petcare.app.ui.components.PetCareTopBar
import com.petcare.app.ui.theme.Spacing

/** Add or edit an expense entry. */
@Composable
fun ExpenseFormScreen(onBack: () -> Unit, onSaved: () -> Unit) {
    val viewModel = appViewModel { c, handle ->
        ExpenseFormViewModel(c.authRepository, c.expenseRepository, handle)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.success) { if (state.success) onSaved() }

    val submitting = state.isSubmitting || state.success
    BackHandler {
        if (!submitting) onBack()
    }

    Scaffold(topBar = {
        PetCareTopBar(
            title = if (state.isEdit) "Edit expense" else "Add expense",
            onBack = { if (!submitting) onBack() }
        )
    }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(Spacing.medium)
        ) {
            FormTextField(
                value = state.amount,
                onValueChange = viewModel::onAmountChange,
                label = "Amount (£)",
                required = true,
                error = state.amountError,
                keyboardType = KeyboardType.Decimal,
                enabled = !submitting
            )
            Spacer(Modifier.height(Spacing.small))
            FormTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = "Description",
                required = true,
                error = state.descriptionError,
                placeholder = "e.g. Vet check-up",
                enabled = !submitting
            )
            Spacer(Modifier.height(Spacing.small))
            DropdownField(
                value = state.category,
                options = ExpenseCategory.entries,
                onSelect = viewModel::onCategoryChange,
                label = "Category",
                optionLabel = { it.label },
                enabled = !submitting
            )
            Spacer(Modifier.height(Spacing.small))
            DateField(
                value = state.date,
                onSelect = viewModel::onDateChange,
                label = "Date",
                enabled = !submitting
            )
            Spacer(Modifier.height(Spacing.small))
            FormTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = "Notes",
                singleLine = false,
                minLines = 2,
                placeholder = "Optional additional notes",
                enabled = !submitting
            )

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
                } else {
                    Text("Save expense")
                }
            }
        }
    }
}
