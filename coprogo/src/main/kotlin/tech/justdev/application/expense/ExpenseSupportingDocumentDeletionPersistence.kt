package tech.justdev.application.expense

import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.entity.ExpenseSupportingDocumentDeletion
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.shared.valueobject.GroupId

interface ExpenseSupportingDocumentDeletionPersistence {
    suspend fun <T> inTransaction(block: suspend (ExpenseSupportingDocumentDeletionPersistenceScope) -> T): T
}

interface ExpenseSupportingDocumentDeletionPersistenceScope {
    suspend fun findExpense(
        id: ExpenseId,
        group: GroupId,
    ): Expense?

    suspend fun persist(deletion: ExpenseSupportingDocumentDeletion)
}
