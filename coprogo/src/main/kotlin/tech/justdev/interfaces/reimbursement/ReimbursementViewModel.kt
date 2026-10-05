package tech.justdev.interfaces.reimbursement

import io.micronaut.serde.annotation.Serdeable
import java.net.URI
import java.time.Instant
import java.util.UUID

@Serdeable
enum class ReimbursementStatusResponse {
    PENDING_REVIEW,
    ACCEPTED,
    REJECTED,
}

@Serdeable
data class ReimbursementResponse(
    val id: UUID,
    val paidBy: String,
    val receivedBy: String,
    val amountInCents: Long,
    val reimbursedAt: Instant,
    val declaredBy: String,
    val declaredAt: Instant,
    val status: ReimbursementStatusResponse,
    val decidedAt: Instant?,
    val rejectionReason: String?,
    val documents: List<ReimbursementSupportingDocumentResponse>,
)

@Serdeable
data class ReimbursementSupportingDocumentResponse(
    val sourceUploadIntent: UUID,
    val fileName: String,
    val mediaType: String,
    val sizeBytes: Long,
    val uploader: String,
    val attachedAt: Instant,
    val download: ReimbursementDocumentDownloadResponse,
)

@Serdeable
data class ReimbursementDocumentDownloadResponse(
    val url: URI,
    val expiresAt: Instant,
)
