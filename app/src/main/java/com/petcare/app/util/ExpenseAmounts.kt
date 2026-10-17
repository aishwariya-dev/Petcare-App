package com.petcare.app.util

/** Shared form/repository rule; SQLite must never receive NaN or an infinite amount. */
object ExpenseAmounts {
    const val ERROR = "Enter a valid positive amount"

    fun isValid(amount: Double): Boolean = amount.isFinite() && amount > 0.0
}