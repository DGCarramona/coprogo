package tech.justdev.interfaces.reimbursement

import jakarta.inject.Singleton
import tech.justdev.application.document.DocumentDownloadRequest
import tech.justdev.application.document.DocumentStorage
import tech.justdev.domain.reimbursement.entity.Reimbursement
import tech.justdev.domain.reimbursement.entity.ReimbursementSupportingDocument
import tech.justdev.domain.reimbursement.valueobject.ReimbursementStatus
import tech.justdev.interfaces.configuration.SupportingDocumentDownloadConfiguration

@Singleton
class ReimbursementPresenter(
    private val documentStorage: DocumentStorage,
    private val configuration: SupportingDocumentDownloadConfiguration,
) {
    suspend fun present(reimbursement: Reimbursement): ReimbursementResponse {
        val status = reimbursement.status.toResponse()

        return ReimbursementResponse(
            id = reimbursement.id.toPrimitive(),
            paidBy = reimbursement.paidBy.toPrimitive(),
            receivedBy = reimbursement.receivedBy.toPrimitive(),
            amountInCents = reimbursement.amount.inCents(),
            reimbursedAt = reimbursement.reimbursedAt,
            declaredBy = reimbursement.declaredBy.toPrimitive(),
            declaredAt = reimbursement.declaredAt,
            status = status.status,
            decidedAt = status.decidedAt,
            rejectionReason = status.rejectionReason,
            documents = reimbursement.supportingDocuments.map { document -> document.toResponse() },
        )
    }

    private suspend fun ReimbursementSupportingDocument.toResponse(): ReimbursementSupportingDocumentResponse =
        ReimbursementSupportingDocumentResponse(
            sourceUploadIntent = sourceUploadIntent.toPrimitive(),
            fileName = fileName.toPrimitive(),
            mediaType = metadata.mediaType.toPrimitive(),
            sizeBytes = metadata.size.toBytes(),
            uploader = uploader.toPrimitive(),
            attachedAt = attachedAt,
            download =
                documentStorage
                    .presignDownload(
                        DocumentDownloadRequest(
                            key = storageKey,
                            fileName = fileName,
                            validFor = configuration.validFor,
                        ),
                    ).let { target -> ReimbursementDocumentDownloadResponse(target.uri, target.expiresAt) },
        )
}

private data class ReimbursementStatusResponseValues(
    val status: ReimbursementStatusResponse,
    val decidedAt: java.time.Instant?,
    val rejectionReason: String?,
)

private fun ReimbursementStatus.toResponse(): ReimbursementStatusResponseValues =
    when (this) {
        ReimbursementStatus.PendingReview -> {
            ReimbursementStatusResponseValues(ReimbursementStatusResponse.PENDING_REVIEW, null, null)
        }

        is ReimbursementStatus.Accepted -> {
            ReimbursementStatusResponseValues(ReimbursementStatusResponse.ACCEPTED, acceptedAt, null)
        }

        is ReimbursementStatus.Rejected -> {
            ReimbursementStatusResponseValues(ReimbursementStatusResponse.REJECTED, decidedAt, reason?.toPrimitive())
        }
    }
