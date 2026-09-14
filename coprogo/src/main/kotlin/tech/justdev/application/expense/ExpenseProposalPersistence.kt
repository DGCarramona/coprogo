package tech.justdev.application.expense

import tech.justdev.domain.document.entity.DocumentUploadIntent
import tech.justdev.domain.document.entity.ExpenseSupportingDocumentAttachment
import tech.justdev.domain.expense.entity.Expense

interface ExpenseProposalPersistence {
    suspend fun persist(
        expense: Expense,
        consumedUploadIntents: List<DocumentUploadIntent>,
        attachments: List<ExpenseSupportingDocumentAttachment>,
    )
}
