package tech.justdev.domain.expense.repository

import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.valueobject.ExpenseId

interface ExpenseRepository {
    fun findById(expense: ExpenseId): Expense?

    fun findProposedById(expense: ExpenseId): Expense?

    fun save(expense: Expense)
}
