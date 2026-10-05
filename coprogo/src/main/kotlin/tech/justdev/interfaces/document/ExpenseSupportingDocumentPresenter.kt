package tech.justdev.interfaces.document

import jakarta.inject.Singleton
import tech.justdev.application.document.DocumentDownloadRequest
import tech.justdev.application.document.DocumentStorage
import tech.justdev.application.document.ExpenseSupportingDocumentAuditAction
import tech.justdev.application.document.ExpenseSupportingDocumentAuditEntrySnapshot
import tech.justdev.application.document.ListExpenseSupportingDocumentsResult
import tech.justdev.application.document.ListedExpenseSupportingDocument
import tech.justdev.interfaces.configuration.SupportingDocumentDownloadConfiguration

@Singleton
class ExpenseSupportingDocumentPresenter(
    private val documentStorage: DocumentStorage,
    private val configuration: SupportingDocumentDownloadConfiguration,
) {
    suspend fun present(result: ListExpenseSupportingDocumentsResult): ExpenseSupportingDocumentsResponse {
        val history = result.history.map { document -> document.toResponse() }
        val historyByUploadIntent = history.associateBy(ExpenseSupportingDocumentResponse::sourceUploadIntent)

        return ExpenseSupportingDocumentsResponse(
            current =
                result.current.map { listedDocument ->
                    historyByUploadIntent.getValue(listedDocument.document.sourceUploadIntent.toPrimitive())
                },
            history = history,
        )
    }

    fun presentAuditTrail(entries: List<ExpenseSupportingDocumentAuditEntrySnapshot>): List<ExpenseSupportingDocumentAuditEntryResponse> =
        entries.map { entry -> entry.toResponse() }

    private suspend fun ListedExpenseSupportingDocument.toResponse(): ExpenseSupportingDocumentResponse =
        documentStorage
            .presignDownload(
                DocumentDownloadRequest(
                    key = document.storageKey,
                    fileName = document.fileName,
                    validFor = configuration.validFor,
                ),
            ).let { download ->
                ExpenseSupportingDocumentResponse(
                    sourceUploadIntent = document.sourceUploadIntent.toPrimitive(),
                    fileName = document.fileName.toPrimitive(),
                    mediaType = document.metadata.mediaType.toPrimitive(),
                    sizeBytes = document.metadata.size.toBytes(),
                    uploader = document.uploader.toPrimitive(),
                    attachedAt = document.attachedAt,
                    replacesSourceUploadIntent = document.replacesSourceUploadIntent?.toPrimitive(),
                    deletion =
                        document.deletion?.let { deletion ->
                            SupportingDocumentDeletionResponse(deletion.deletedBy.toPrimitive(), deletion.deletedAt)
                        },
                    canDelete = canDelete,
                    download = SupportingDocumentDownloadResponse(download.uri, download.expiresAt),
                )
            }
}

private fun ExpenseSupportingDocumentAuditEntrySnapshot.toResponse(): ExpenseSupportingDocumentAuditEntryResponse =
    ExpenseSupportingDocumentAuditEntryResponse(
        action = action.toResponse(),
        occurredAt = occurredAt,
        performedBy = performedBy.toPrimitive(),
        documentUploadIntent = documentUploadIntent.toPrimitive(),
        replacedDocumentUploadIntent = replacedDocumentUploadIntent?.toPrimitive(),
        fileName = fileName.toPrimitive(),
    )

private fun ExpenseSupportingDocumentAuditAction.toResponse(): ExpenseSupportingDocumentAuditActionResponse =
    when (this) {
        ExpenseSupportingDocumentAuditAction.ATTACHED -> ExpenseSupportingDocumentAuditActionResponse.ATTACHED
        ExpenseSupportingDocumentAuditAction.REPLACED -> ExpenseSupportingDocumentAuditActionResponse.REPLACED
        ExpenseSupportingDocumentAuditAction.DELETED -> ExpenseSupportingDocumentAuditActionResponse.DELETED
    }
