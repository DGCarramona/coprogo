package tech.justdev.domain.expense.repository

import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.valueobject.ExpenseId

interface ExpenseRepository {
    suspend fun findById(id: ExpenseId): Expense?

    suspend fun findProposedById(id: ExpenseId): Expense?

    suspend fun save(expense: Expense)
}
