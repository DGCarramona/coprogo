package tech.justdev.application.expense

import tech.justdev.domain.document.entity.DocumentUploadIntent
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.entity.ExpenseSupportingDocumentReplacement
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId

interface ExpenseSupportingDocumentReplacementPersistence {
    suspend fun <T> inTransaction(block: suspend (ExpenseSupportingDocumentReplacementPersistenceScope) -> T): T
}

interface ExpenseSupportingDocumentReplacementPersistenceScope {
    suspend fun findExpense(
        id: ExpenseId,
        group: GroupId,
    ): Expense?

    suspend fun findReadyReplacementUploadIntent(
        id: DocumentUploadIntentId,
        group: GroupId,
        uploader: MemberEmail,
    ): DocumentUploadIntent?

    suspend fun persist(
        replacement: ExpenseSupportingDocumentReplacement,
        consumedReplacementIntent: DocumentUploadIntent,
    )
}
