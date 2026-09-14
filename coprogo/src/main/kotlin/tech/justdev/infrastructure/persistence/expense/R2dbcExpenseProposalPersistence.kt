package tech.justdev.infrastructure.persistence.expense

import jakarta.inject.Singleton
import tech.justdev.application.expense.ExpenseProposalPersistence
import tech.justdev.application.shared.TransactionRunner
import tech.justdev.domain.document.entity.DocumentUploadIntent
import tech.justdev.domain.document.entity.ExpenseSupportingDocumentAttachment
import tech.justdev.domain.document.repository.DocumentUploadIntentRepository
import tech.justdev.domain.document.repository.ExpenseSupportingDocumentAttachmentRepository
import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.repository.ExpenseRepository

@Singleton
class R2dbcExpenseProposalPersistence(
    private val transactionRunner: TransactionRunner,
    private val documentUploadIntentRepository: DocumentUploadIntentRepository,
    private val expenseRepository: ExpenseRepository,
    private val attachmentRepository: ExpenseSupportingDocumentAttachmentRepository,
) : ExpenseProposalPersistence {
    override suspend fun persist(
        expense: Expense,
        consumedUploadIntents: List<DocumentUploadIntent>,
        attachments: List<ExpenseSupportingDocumentAttachment>,
    ) {
        transactionRunner.transaction {
            documentUploadIntentRepository.persistAll(consumedUploadIntents)
            expenseRepository.persist(expense)
            attachmentRepository.persistAll(attachments)
        }
    }
}
