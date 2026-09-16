package tech.justdev.application.expense

import tech.justdev.domain.document.entity.DocumentUploadIntent
import tech.justdev.domain.document.entity.ExpenseSupportingDocumentAttachment
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId

interface ExpenseSupportingDocumentReplacement {
    suspend fun <T> inTransaction(block: suspend (ExpenseSupportingDocumentReplacementScope) -> T): T
}

interface ExpenseSupportingDocumentReplacementScope {
    suspend fun findExpense(
        id: ExpenseId,
        group: GroupId,
    ): Expense?

    suspend fun findCurrentAttachment(
        sourceUploadIntent: DocumentUploadIntentId,
        expense: ExpenseId,
        group: GroupId,
    ): ExpenseSupportingDocumentAttachment?

    suspend fun findReadyReplacementUploadIntent(
        id: DocumentUploadIntentId,
        group: GroupId,
        uploader: MemberEmail,
    ): DocumentUploadIntent?

    suspend fun persist(
        consumedReplacementUploadIntent: DocumentUploadIntent,
        replacementAttachment: ExpenseSupportingDocumentAttachment,
    )
}
