package tech.justdev.domain.expense.repository

import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.shared.valueobject.GroupId

interface ExpenseRepository {
    suspend fun findByIdAndGroup(
        id: ExpenseId,
        group: GroupId,
    ): Expense?

    suspend fun findByGroup(group: GroupId): List<Expense>

    suspend fun findProposedByIdAndGroup(
        id: ExpenseId,
        group: GroupId,
    ): Expense?

    suspend fun persist(expense: Expense)
}
