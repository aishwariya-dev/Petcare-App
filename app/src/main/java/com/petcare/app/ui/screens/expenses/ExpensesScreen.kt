package com.petcare.app.ui.screens.expenses

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petcare.app.model.Expense
import com.petcare.app.ui.common.UiState
import com.petcare.app.ui.common.appViewModel
import com.petcare.app.ui.components.ConfirmationDialog
import com.petcare.app.ui.components.EmptyState
import com.petcare.app.ui.components.ErrorState
import com.petcare.app.ui.components.LoadingState
import com.petcare.app.ui.components.PetCareTopBar
import com.petcare.app.ui.theme.Spacing
import com.petcare.app.util.DateTimeFormats

/** Expense tracker screen — lists all pet-related costs and a running total. */
@Composable
fun ExpensesScreen(
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    onEditExpense: (Long) -> Unit
) {
    val viewModel = appViewModel { c, _ ->
        ExpensesViewModel(c.authRepository, c.expenseRepository)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<Expense?>(null) }

    Scaffold(
        topBar = { PetCareTopBar(title = "Expenses", onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddExpense) {
                Icon(Icons.Filled.Add, contentDescription = "Add expense")
            }
        }
    ) { padding ->
        when (val s = state) {
            UiState.Loading -> LoadingState(Modifier.padding(padding))
            is UiState.Error -> ErrorState(s.message, Modifier.padding(padding))
            is UiState.Success -> {
                val data = s.data
                if (data.expenses.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.Payments,
                        title = "No expenses yet",
                        message = "Track food, vet visits, grooming, and more.",
                        actionLabel = "Add expense",
                        onAction = onAddExpense,
                        modifier = Modifier.padding(padding)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.padding(padding).fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Spacing.medium, end = Spacing.medium, bottom = 96.dp
                        )
                    ) {
                        // Total card
                        item {
                            Spacer(Modifier.height(Spacing.small))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.medium)
                            ) {
                                Row(
                                    modifier = Modifier.padding(Spacing.medium).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            "Total Spending",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            "£%.2f".format(data.total),
                                            style = MaterialTheme.typography.headlineMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Icon(
                                        Icons.Outlined.AttachMoney,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        items(data.expenses, key = { it.id }) { expense ->
                            ExpenseItem(
                                expense = expense,
                                onEdit = { onEditExpense(expense.id) },
                                onDelete = { toDelete = expense }
                            )
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { expense ->
        ConfirmationDialog(
            title = "Delete expense?",
            message = "\"${expense.description}\" (£%.2f) will be removed.".format(expense.amount),
            confirmLabel = "Delete",
            onConfirm = { toDelete = null; viewModel.delete(expense) },
            onDismiss = { toDelete = null }
        )
    }
}

@Composable
private fun ExpenseItem(expense: Expense, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.small)
    ) {
        Column(Modifier.padding(start = Spacing.medium, end = Spacing.xs, top = Spacing.small, bottom = Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(expense.description, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${expense.categoryType.label}  •  ${DateTimeFormats.fullDate(expense.date)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (expense.notes.isNotBlank()) {
                        Text(
                            expense.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    "£%.2f".format(expense.amount),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            HorizontalDivider(Modifier.padding(vertical = Spacing.xs))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit expense",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete expense",
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
