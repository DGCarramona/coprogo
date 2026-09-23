package tech.justdev.application.document

import jakarta.inject.Singleton
import tech.justdev.application.expense.ExpenseNotFoundException
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.domain.document.entity.ExpenseSupportingDocument
import tech.justdev.domain.document.valueobject.DocumentFileName
import tech.justdev.domain.document.valueobject.DocumentMediaType
import tech.justdev.domain.document.valueobject.DocumentSize
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.document.valueobject.SupportingDocumentAttachmentDeletion
import tech.justdev.domain.expense.repository.ExpenseRepository
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId
import java.time.Duration
import java.time.Instant

data class ListExpenseSupportingDocumentsQuery(
    val group: GroupId,
    val expense: ExpenseId,
    val requestedBy: MemberEmail,
    val downloadValidFor: Duration,
)

data class ExpenseSupportingDocumentSnapshot(
    val sourceUploadIntent: DocumentUploadIntentId,
    val fileName: DocumentFileName,
    val mediaType: DocumentMediaType,
    val size: DocumentSize,
    val uploader: MemberEmail,
    val attachedAt: Instant,
    val replacesSourceUploadIntent: DocumentUploadIntentId?,
    val deletion: SupportingDocumentAttachmentDeletion?,
    val download: DocumentDownloadTarget,
)

data class ListExpenseSupportingDocumentsResult(
    val current: List<ExpenseSupportingDocumentSnapshot>,
    val history: List<ExpenseSupportingDocumentSnapshot>,
)

interface ListExpenseSupportingDocumentsUseCase {
    suspend operator fun invoke(query: ListExpenseSupportingDocumentsQuery): ListExpenseSupportingDocumentsResult
}

@Singleton
class ListExpenseSupportingDocumentsUseCaseImpl(
    private val groupAccessPolicy: GroupAccessPolicy,
    private val expenseRepository: ExpenseRepository,
    private val documentStorage: DocumentStorage,
) : ListExpenseSupportingDocumentsUseCase {
    override suspend operator fun invoke(query: ListExpenseSupportingDocumentsQuery): ListExpenseSupportingDocumentsResult {
        groupAccessPolicy.requireMember(query.group, query.requestedBy)

        val expense =
            expenseRepository.findByIdAndGroup(query.expense, query.group)
                ?: throw ExpenseNotFoundException(query.expense, query.group)
        val history = expense.supportingDocuments.all
        val currentIntentIds =
            expense.supportingDocuments.current
                .map(ExpenseSupportingDocument::sourceUploadIntent)
                .toSet()

        if (history.isEmpty()) {
            return ListExpenseSupportingDocumentsResult(current = emptyList(), history = emptyList())
        }

        val historySnapshots =
            history.map { document ->
                toSnapshot(
                    document,
                    documentStorage.presignDownload(
                        DocumentDownloadRequest(
                            key = document.storageKey,
                            fileName = document.fileName,
                            validFor = query.downloadValidFor,
                        ),
                    ),
                )
            }

        return ListExpenseSupportingDocumentsResult(
            current = historySnapshots.filter { it.sourceUploadIntent in currentIntentIds },
            history = historySnapshots,
        )
    }

    private fun toSnapshot(
        document: ExpenseSupportingDocument,
        download: DocumentDownloadTarget,
    ): ExpenseSupportingDocumentSnapshot =
        ExpenseSupportingDocumentSnapshot(
            sourceUploadIntent = document.sourceUploadIntent,
            fileName = document.fileName,
            mediaType = document.metadata.mediaType,
            size = document.metadata.size,
            uploader = document.uploader,
            attachedAt = document.attachedAt,
            replacesSourceUploadIntent = document.replacesSourceUploadIntent,
            deletion = document.deletion,
            download = download,
        )
}
