package tech.justdev.domain.expense.repository

import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.shared.valueobject.GroupId

interface ExpenseRepository {
    suspend fun findById(id: ExpenseId): Expense?

    suspend fun findByGroup(group: GroupId): List<Expense>

    suspend fun findProposedById(id: ExpenseId): Expense?

    suspend fun persist(expense: Expense)
}
