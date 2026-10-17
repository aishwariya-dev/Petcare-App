package com.petcare.app.ui.screens.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petcare.app.data.repository.AuthRepository
import com.petcare.app.data.repository.ExpenseRepository
import com.petcare.app.model.Expense
import com.petcare.app.ui.common.UiState
import com.petcare.app.ui.common.userMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExpensesData(
    val expenses: List<Expense>,
    val total: Double
)

/** Screen 11 — pet expense tracker. */
class ExpensesViewModel(
    authRepository: AuthRepository,
    private val expenseRepository: ExpenseRepository
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<UiState<ExpensesData>> = authRepository.currentUser
        .flatMapLatest { user ->
            if (user == null) return@flatMapLatest flowOf<UiState<ExpensesData>>(UiState.Loading)
            combine(
                expenseRepository.observeExpenses(user.id),
                expenseRepository.observeTotal(user.id)
            ) { expenses, total ->
                UiState.Success(ExpensesData(expenses = expenses, total = total ?: 0.0))
            }
        }
        .catch { e -> emit(UiState.Error(e.userMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    fun delete(expense: Expense) {
        viewModelScope.launch { expenseRepository.delete(expense) }
    }
}
