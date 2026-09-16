package tech.justdev.domain.document.repository

import tech.justdev.domain.document.entity.ExpenseSupportingDocumentAttachment
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.shared.valueobject.GroupId

interface ExpenseSupportingDocumentAttachmentRepository {
    suspend fun persist(attachment: ExpenseSupportingDocumentAttachment)

    suspend fun persistAll(attachments: List<ExpenseSupportingDocumentAttachment>)

    suspend fun findCurrentByExpenseAndGroup(
        expense: ExpenseId,
        group: GroupId,
    ): List<ExpenseSupportingDocumentAttachment>

    suspend fun findCurrentBySourceUploadIntentAndExpenseAndGroup(
        sourceUploadIntent: DocumentUploadIntentId,
        expense: ExpenseId,
        group: GroupId,
    ): ExpenseSupportingDocumentAttachment?

    suspend fun findHistoryByExpenseAndGroup(
        expense: ExpenseId,
        group: GroupId,
    ): List<ExpenseSupportingDocumentAttachment>
}
