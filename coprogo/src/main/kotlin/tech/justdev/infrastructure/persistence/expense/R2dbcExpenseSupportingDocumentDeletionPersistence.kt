package tech.justdev.infrastructure.persistence.expense

import jakarta.inject.Singleton
import tech.justdev.application.expense.ExpenseSupportingDocumentDeletionPersistence
import tech.justdev.application.expense.ExpenseSupportingDocumentDeletionPersistenceScope
import tech.justdev.application.shared.TransactionRunner
import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.entity.ExpenseSupportingDocumentDeletion
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.infrastructure.persistence.document.R2dbcExpenseSupportingDocumentAttachmentRepository

@Singleton
class R2dbcExpenseSupportingDocumentDeletionPersistence(
    private val transactionRunner: TransactionRunner,
    private val expenseRepository: R2dbcExpenseRepository,
    private val attachmentRepository: R2dbcExpenseSupportingDocumentAttachmentRepository,
) : ExpenseSupportingDocumentDeletionPersistence {
    override suspend fun <T> inTransaction(block: suspend (ExpenseSupportingDocumentDeletionPersistenceScope) -> T): T =
        transactionRunner.transaction {
            block(
                R2dbcExpenseSupportingDocumentDeletionPersistenceScope(
                    expenseRepository = expenseRepository,
                    attachmentRepository = attachmentRepository,
                ),
            )
        }
}

private class R2dbcExpenseSupportingDocumentDeletionPersistenceScope(
    private val expenseRepository: R2dbcExpenseRepository,
    private val attachmentRepository: R2dbcExpenseSupportingDocumentAttachmentRepository,
) : ExpenseSupportingDocumentDeletionPersistenceScope {
    override suspend fun findExpense(
        id: ExpenseId,
        group: GroupId,
    ): Expense? = expenseRepository.findByIdAndGroupForUpdate(id, group)

    override suspend fun persist(deletion: ExpenseSupportingDocumentDeletion) {
        attachmentRepository.persistAll(listOf(deletion.deleted))
    }
}
