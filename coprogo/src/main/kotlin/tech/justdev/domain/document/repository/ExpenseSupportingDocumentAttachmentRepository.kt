package tech.justdev.domain.document.repository

import tech.justdev.domain.document.entity.ExpenseSupportingDocumentAttachment
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.shared.valueobject.GroupId

interface ExpenseSupportingDocumentAttachmentRepository {
    suspend fun persist(attachment: ExpenseSupportingDocumentAttachment)

    suspend fun findByExpenseAndGroup(
        expense: ExpenseId,
        group: GroupId,
    ): List<ExpenseSupportingDocumentAttachment>
}
