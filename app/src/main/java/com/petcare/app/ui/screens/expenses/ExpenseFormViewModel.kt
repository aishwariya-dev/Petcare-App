package com.petcare.app.ui.screens.expenses

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petcare.app.data.repository.AuthRepository
import com.petcare.app.data.repository.ExpenseRepository
import com.petcare.app.model.Expense
import com.petcare.app.model.ExpenseCategory
import com.petcare.app.navigation.Routes
import com.petcare.app.ui.common.userMessage
import com.petcare.app.util.ExpenseAmounts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ExpenseFormState(
    val isEdit: Boolean = false,
    val amount: String = "",
    val category: ExpenseCategory = ExpenseCategory.OTHER,
    val description: String = "",
    val notes: String = "",
    val date: LocalDate = LocalDate.now(),
    val amountError: String? = null,
    val descriptionError: String? = null,
    val formError: String? = null,
    val isSubmitting: Boolean = false,
    val success: Boolean = false
)

class ExpenseFormViewModel(
    private val authRepository: AuthRepository,
    private val expenseRepository: ExpenseRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val expenseId = savedStateHandle.get<Long>(Routes.ARG_EXPENSE_ID)?.takeIf { it > 0 }

    private val _state = MutableStateFlow(ExpenseFormState(isEdit = expenseId != null))
    val state: StateFlow<ExpenseFormState> = _state.asStateFlow()

    init {
        if (expenseId != null) {
            viewModelScope.launch {
                val expense = expenseRepository.getExpense(expenseId) ?: return@launch
                _state.update {
                    it.copy(
                        amount = expense.amount.toString(),
                        category = expense.categoryType,
                        description = expense.description,
                        notes = expense.notes,
                        date = expense.date
                    )
                }
            }
        }
    }

    fun onAmountChange(v: String) = _state.update { it.copy(amount = v, amountError = null) }
    fun onCategoryChange(v: ExpenseCategory) = _state.update { it.copy(category = v) }
    fun onDescriptionChange(v: String) = _state.update { it.copy(description = v, descriptionError = null) }
    fun onNotesChange(v: String) = _state.update { it.copy(notes = v) }
    fun onDateChange(v: LocalDate) = _state.update { it.copy(date = v) }

    fun submit() {
        if (_state.value.isSubmitting || _state.value.success) return
        val s = _state.value
        val amountVal = s.amount.trim().toDoubleOrNull()
        val amountError = when {
            s.amount.isBlank() -> "Amount is required"
            amountVal == null || !ExpenseAmounts.isValid(amountVal) -> ExpenseAmounts.ERROR
            else -> null
        }
        val descriptionError = if (s.description.isBlank()) "Description is required" else null
        if (amountError != null || descriptionError != null) {
            _state.update { it.copy(amountError = amountError, descriptionError = descriptionError) }
            return
        }
        _state.update { it.copy(isSubmitting = true, formError = null) }
        viewModelScope.launch {
            try {
                val userId = authRepository.currentUser.first()?.id
                    ?: throw IllegalStateException("Not signed in")
                val expense = Expense(
                    id = expenseId ?: 0L,
                    ownerId = userId,
                    amount = amountVal!!,
                    category = s.category.name,
                    description = s.description.trim(),
                    notes = s.notes.trim(),
                    dateEpochDay = s.date.toEpochDay()
                )
                expenseRepository.save(expense)
                _state.update { it.copy(success = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(formError = e.userMessage()) }
            } finally {
                _state.update { it.copy(isSubmitting = false) }
            }
        }
    }
}
