package tech.justdev.infrastructure.persistence.expense

import jakarta.inject.Singleton
import tech.justdev.application.expense.ExpenseSupportingDocumentReplacementPersistence
import tech.justdev.application.expense.ExpenseSupportingDocumentReplacementPersistenceScope
import tech.justdev.application.shared.TransactionRunner
import tech.justdev.domain.document.entity.DocumentUploadIntent
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.entity.ExpenseSupportingDocumentReplacement
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.infrastructure.persistence.document.R2dbcDocumentUploadIntentRepository
import tech.justdev.infrastructure.persistence.document.R2dbcExpenseSupportingDocumentAttachmentRepository

@Singleton
class R2dbcExpenseSupportingDocumentReplacementPersistence(
    private val transactionRunner: TransactionRunner,
    private val expenseRepository: R2dbcExpenseRepository,
    private val documentUploadIntentRepository: R2dbcDocumentUploadIntentRepository,
    private val attachmentRepository: R2dbcExpenseSupportingDocumentAttachmentRepository,
) : ExpenseSupportingDocumentReplacementPersistence {
    override suspend fun <T> inTransaction(block: suspend (ExpenseSupportingDocumentReplacementPersistenceScope) -> T): T =
        transactionRunner.transaction {
            block(
                R2dbcExpenseSupportingDocumentReplacementPersistenceScope(
                    expenseRepository = expenseRepository,
                    documentUploadIntentRepository = documentUploadIntentRepository,
                    attachmentRepository = attachmentRepository,
                ),
            )
        }
}

private class R2dbcExpenseSupportingDocumentReplacementPersistenceScope(
    private val expenseRepository: R2dbcExpenseRepository,
    private val documentUploadIntentRepository: R2dbcDocumentUploadIntentRepository,
    private val attachmentRepository: R2dbcExpenseSupportingDocumentAttachmentRepository,
) : ExpenseSupportingDocumentReplacementPersistenceScope {
    override suspend fun findExpense(
        id: ExpenseId,
        group: GroupId,
    ): Expense? = expenseRepository.findByIdAndGroupForUpdate(id, group)

    override suspend fun findReadyReplacementUploadIntent(
        id: DocumentUploadIntentId,
        group: GroupId,
        uploader: MemberEmail,
    ): DocumentUploadIntent? =
        documentUploadIntentRepository
            .findReadyByIdsAndGroupAndUploaderForUpdate(
                ids = setOf(id),
                group = group,
                uploader = uploader,
            ).singleOrNull()
            ?.takeIf { intent -> intent.id == id }

    override suspend fun persist(
        consumedReplacementUploadIntent: DocumentUploadIntent,
        replacement: ExpenseSupportingDocumentReplacement,
    ) {
        documentUploadIntentRepository.persistAll(listOf(consumedReplacementUploadIntent))
        attachmentRepository.persistAll(listOf(replacement.replacement))
    }
}
