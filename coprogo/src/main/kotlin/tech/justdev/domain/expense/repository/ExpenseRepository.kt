package tech.justdev.domain.expense.repository

import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.valueobject.ExpenseId

interface ExpenseRepository {
    fun findById(expenseId: ExpenseId): Expense?

    fun save(expense: Expense)
}
