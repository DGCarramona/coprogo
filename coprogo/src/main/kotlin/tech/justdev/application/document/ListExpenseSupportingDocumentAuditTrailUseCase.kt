package tech.justdev.application.document

import jakarta.inject.Singleton
import tech.justdev.application.expense.ExpenseNotFoundException
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.domain.document.entity.ExpenseSupportingDocument
import tech.justdev.domain.document.valueobject.DocumentFileName
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.expense.repository.ExpenseRepository
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId
import java.time.Instant

data class ListExpenseSupportingDocumentAuditTrailQuery(
    val group: GroupId,
    val expense: ExpenseId,
    val requestedBy: MemberEmail,
)

enum class ExpenseSupportingDocumentAuditAction {
    ATTACHED,
    REPLACED,
    DELETED,
}

data class ExpenseSupportingDocumentAuditEntrySnapshot(
    val action: ExpenseSupportingDocumentAuditAction,
    val occurredAt: Instant,
    val performedBy: MemberEmail,
    val documentUploadIntent: DocumentUploadIntentId,
    val replacedDocumentUploadIntent: DocumentUploadIntentId?,
    val fileName: DocumentFileName,
)

interface ListExpenseSupportingDocumentAuditTrailUseCase {
    suspend operator fun invoke(query: ListExpenseSupportingDocumentAuditTrailQuery): List<ExpenseSupportingDocumentAuditEntrySnapshot>
}

@Singleton
class ListExpenseSupportingDocumentAuditTrailUseCaseImpl(
    private val groupAccessPolicy: GroupAccessPolicy,
    private val expenseRepository: ExpenseRepository,
) : ListExpenseSupportingDocumentAuditTrailUseCase {
    override suspend operator fun invoke(
        query: ListExpenseSupportingDocumentAuditTrailQuery,
    ): List<ExpenseSupportingDocumentAuditEntrySnapshot> {
        groupAccessPolicy.requireMember(query.group, query.requestedBy)

        return expenseRepository
            .findByIdAndGroup(query.expense, query.group)
            ?.supportingDocuments
            ?.all
            ?.flatMap(::eventsFor)
            ?.sortedWith(
                compareBy<ExpenseSupportingDocumentAuditEntrySnapshot> { it.occurredAt }
                    .thenBy { it.action }
                    .thenBy { it.documentUploadIntent.toPrimitive() },
            )
            ?: throw ExpenseNotFoundException(query.expense, query.group)
    }

    private fun eventsFor(document: ExpenseSupportingDocument): List<ExpenseSupportingDocumentAuditEntrySnapshot> {
        val attachment =
            document.replacesSourceUploadIntent
                ?.let { replacedDocumentUploadIntent ->
                    ExpenseSupportingDocumentAuditEntrySnapshot(
                        action = ExpenseSupportingDocumentAuditAction.REPLACED,
                        occurredAt = document.attachedAt,
                        performedBy = document.uploader,
                        documentUploadIntent = document.sourceUploadIntent,
                        replacedDocumentUploadIntent = replacedDocumentUploadIntent,
                        fileName = document.fileName,
                    )
                }
                ?: ExpenseSupportingDocumentAuditEntrySnapshot(
                    action = ExpenseSupportingDocumentAuditAction.ATTACHED,
                    occurredAt = document.attachedAt,
                    performedBy = document.uploader,
                    documentUploadIntent = document.sourceUploadIntent,
                    replacedDocumentUploadIntent = null,
                    fileName = document.fileName,
                )

        return listOfNotNull(
            attachment,
            document.deletion?.let { deletion ->
                ExpenseSupportingDocumentAuditEntrySnapshot(
                    action = ExpenseSupportingDocumentAuditAction.DELETED,
                    occurredAt = deletion.deletedAt,
                    performedBy = deletion.deletedBy,
                    documentUploadIntent = document.sourceUploadIntent,
                    replacedDocumentUploadIntent = null,
                    fileName = document.fileName,
                )
            },
        )
    }
}
