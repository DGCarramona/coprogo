package tech.justdev.application.expense

import tech.justdev.domain.document.entity.ExpenseSupportingDocumentAttachment
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.shared.valueobject.GroupId

interface ExpenseSupportingDocumentDeletion {
    suspend fun <T> inTransaction(block: suspend (ExpenseSupportingDocumentDeletionScope) -> T): T
}

interface ExpenseSupportingDocumentDeletionScope {
    suspend fun findExpense(
        id: ExpenseId,
        group: GroupId,
    ): Expense?

    suspend fun findCurrentAttachment(
        sourceUploadIntent: DocumentUploadIntentId,
        expense: ExpenseId,
        group: GroupId,
    ): ExpenseSupportingDocumentAttachment?

    suspend fun persist(deletedAttachment: ExpenseSupportingDocumentAttachment)
}
