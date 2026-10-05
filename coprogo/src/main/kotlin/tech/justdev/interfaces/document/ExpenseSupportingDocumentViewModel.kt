package tech.justdev.interfaces.document

import io.micronaut.serde.annotation.Serdeable
import java.net.URI
import java.time.Instant
import java.util.UUID

@Serdeable
data class ExpenseSupportingDocumentsResponse(
    val current: List<ExpenseSupportingDocumentResponse>,
    val history: List<ExpenseSupportingDocumentResponse>,
)

@Serdeable
enum class ExpenseSupportingDocumentAuditActionResponse {
    ATTACHED,
    REPLACED,
    DELETED,
}

@Serdeable
data class ExpenseSupportingDocumentAuditEntryResponse(
    val action: ExpenseSupportingDocumentAuditActionResponse,
    val occurredAt: Instant,
    val performedBy: String,
    val documentUploadIntent: UUID,
    val replacedDocumentUploadIntent: UUID?,
    val fileName: String,
)

@Serdeable
data class ExpenseSupportingDocumentResponse(
    val sourceUploadIntent: UUID,
    val fileName: String,
    val mediaType: String,
    val sizeBytes: Long,
    val uploader: String,
    val attachedAt: Instant,
    val replacesSourceUploadIntent: UUID?,
    val deletion: SupportingDocumentDeletionResponse?,
    val canDelete: Boolean,
    val download: SupportingDocumentDownloadResponse,
)

@Serdeable
data class SupportingDocumentDeletionResponse(
    val deletedBy: String,
    val deletedAt: Instant,
)

@Serdeable
data class SupportingDocumentDownloadResponse(
    val url: URI,
    val expiresAt: Instant,
)
