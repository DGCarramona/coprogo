package tech.justdev.infrastructure.persistence.expense

import jakarta.inject.Singleton
import tech.justdev.application.expense.ExpenseSupportingDocumentDeletion
import tech.justdev.application.expense.ExpenseSupportingDocumentDeletionScope
import tech.justdev.application.shared.TransactionRunner
import tech.justdev.domain.document.entity.ExpenseSupportingDocumentAttachment
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.infrastructure.persistence.document.R2dbcExpenseSupportingDocumentAttachmentRepository

@Singleton
class R2dbcExpenseSupportingDocumentDeletion(
    private val transactionRunner: TransactionRunner,
    private val expenseRepository: R2dbcExpenseRepository,
    private val attachmentRepository: R2dbcExpenseSupportingDocumentAttachmentRepository,
) : ExpenseSupportingDocumentDeletion {
    override suspend fun <T> inTransaction(block: suspend (ExpenseSupportingDocumentDeletionScope) -> T): T =
        transactionRunner.transaction {
            block(
                R2dbcExpenseSupportingDocumentDeletionScope(
                    expenseRepository = expenseRepository,
                    attachmentRepository = attachmentRepository,
                ),
            )
        }
}

private class R2dbcExpenseSupportingDocumentDeletionScope(
    private val expenseRepository: R2dbcExpenseRepository,
    private val attachmentRepository: R2dbcExpenseSupportingDocumentAttachmentRepository,
) : ExpenseSupportingDocumentDeletionScope {
    override suspend fun findExpense(
        id: ExpenseId,
        group: GroupId,
    ): Expense? = expenseRepository.findByIdAndGroupForUpdate(id, group)

    override suspend fun findCurrentAttachment(
        sourceUploadIntent: DocumentUploadIntentId,
        expense: ExpenseId,
        group: GroupId,
    ): ExpenseSupportingDocumentAttachment? =
        attachmentRepository.findCurrentBySourceUploadIntentAndExpenseAndGroupForUpdate(
            sourceUploadIntent = sourceUploadIntent,
            expense = expense,
            group = group,
        )

    override suspend fun persist(deletedAttachment: ExpenseSupportingDocumentAttachment) {
        attachmentRepository.persistAll(listOf(deletedAttachment))
    }
}
