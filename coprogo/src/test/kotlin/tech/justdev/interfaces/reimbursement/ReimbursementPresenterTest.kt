package tech.justdev.interfaces.reimbursement

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tech.justdev.application.document.DocumentDownloadRequest
import tech.justdev.application.document.DocumentDownloadTarget
import tech.justdev.application.document.DocumentStorage
import tech.justdev.application.document.DocumentUploadRequest
import tech.justdev.application.document.DocumentUploadTarget
import tech.justdev.domain.document.entity.DocumentMetadata
import tech.justdev.domain.document.valueobject.DocumentFileName
import tech.justdev.domain.document.valueobject.DocumentMediaType
import tech.justdev.domain.document.valueobject.DocumentSha256
import tech.justdev.domain.document.valueobject.DocumentSize
import tech.justdev.domain.document.valueobject.DocumentStorageKey
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.reimbursement.entity.Reimbursement
import tech.justdev.domain.reimbursement.entity.ReimbursementSupportingDocument
import tech.justdev.domain.reimbursement.valueobject.ReimbursementId
import tech.justdev.domain.reimbursement.valueobject.ReimbursementRejectionReason
import tech.justdev.domain.reimbursement.valueobject.ReimbursementStatus
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.interfaces.configuration.SupportingDocumentDownloadConfiguration
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.testUuid
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.Base64

class ReimbursementPresenterTest {
    private val storage = RecordingDocumentStorage()
    private val configuration = SupportingDocumentDownloadConfiguration().apply { validFor = DOWNLOAD_VALID_FOR }
    private val presenter = ReimbursementPresenter(storage, configuration)

    @Nested
    inner class Present {
        @Test
        fun `should present a pending documented reimbursement with its signed document`() =
            runTest {
                val response = presenter.present(documented(ReimbursementStatus.PendingReview))

                assertEquals(ReimbursementStatusResponse.PENDING_REVIEW, response.status)
                assertEquals(null, response.decidedAt)
                assertEquals(null, response.rejectionReason)
                assertEquals(
                    listOf(
                        ReimbursementSupportingDocumentResponse(
                            sourceUploadIntent = DOCUMENT,
                            fileName = "receipt.pdf",
                            mediaType = "application/pdf",
                            sizeBytes = 256,
                            uploader = "payer@example.com",
                            attachedAt = ATTACHED_AT,
                            download = ReimbursementDocumentDownloadResponse(DOWNLOAD_URI, DOWNLOAD_EXPIRES_AT),
                        ),
                    ),
                    response.documents,
                )
                assertEquals(
                    listOf(DocumentDownloadRequest(STORAGE_KEY, FILE_NAME, DOWNLOAD_VALID_FOR)),
                    storage.downloadRequests,
                )
            }

        @Test
        fun `should present an accepted direct reimbursement without a rejection reason`() =
            runTest {
                val response = presenter.present(direct())

                assertEquals(ReimbursementStatusResponse.ACCEPTED, response.status)
                assertEquals(DECLARED_AT, response.decidedAt)
                assertEquals(null, response.rejectionReason)
                assertEquals(emptyList<ReimbursementSupportingDocumentResponse>(), response.documents)
            }

        @Test
        fun `should present a rejected documented reimbursement with its reason`() =
            runTest {
                val response =
                    presenter.present(
                        documented(ReimbursementStatus.Rejected(DECIDED_AT, ReimbursementRejectionReason.of("Duplicate"))),
                    )

                assertEquals(ReimbursementStatusResponse.REJECTED, response.status)
                assertEquals(DECIDED_AT, response.decidedAt)
                assertEquals("Duplicate", response.rejectionReason)
            }
    }

    private fun direct(): Reimbursement =
        Reimbursement.recordDirect(
            id = REIMBURSEMENT,
            group = GROUP,
            paidBy = PAYER,
            receivedBy = RECEIVER,
            amount = AMOUNT,
            reimbursedAt = REIMBURSED_AT,
            declaredBy = RECEIVER,
            declaredAt = DECLARED_AT,
        )

    private fun documented(status: ReimbursementStatus): Reimbursement =
        Reimbursement.restore(
            id = REIMBURSEMENT,
            group = GROUP,
            paidBy = PAYER,
            receivedBy = RECEIVER,
            amount = AMOUNT,
            reimbursedAt = REIMBURSED_AT,
            declaredBy = PAYER,
            declaredAt = DECLARED_AT,
            status = status,
            supportingDocuments = listOf(document()),
        )

    private fun document(): ReimbursementSupportingDocument =
        ReimbursementSupportingDocument.restore(
            sourceUploadIntent = DocumentUploadIntentId(DOCUMENT),
            group = GROUP,
            uploader = PAYER,
            storageKey = STORAGE_KEY,
            fileName = FILE_NAME,
            metadata = DocumentMetadata(DocumentMediaType.of("application/pdf"), DocumentSize.ofBytes(256), CHECKSUM),
            attachedAt = ATTACHED_AT,
        )

    private class RecordingDocumentStorage : DocumentStorage {
        val downloadRequests = mutableListOf<DocumentDownloadRequest>()

        override suspend fun presignUpload(request: DocumentUploadRequest): DocumentUploadTarget = error("not used")

        override suspend fun inspect(key: DocumentStorageKey): DocumentMetadata? = error("not used")

        override suspend fun presignDownload(request: DocumentDownloadRequest): DocumentDownloadTarget =
            DocumentDownloadTarget(DOWNLOAD_URI, DOWNLOAD_EXPIRES_AT).also { downloadRequests += request }
    }

    private companion object {
        val GROUP: GroupId = groupId("reimbursement-presenter")
        val REIMBURSEMENT = ReimbursementId(testUuid("reimbursement-presenter"))
        val DOCUMENT = testUuid("reimbursement-presenter-document")
        val PAYER: MemberEmail = memberEmail("payer")
        val RECEIVER: MemberEmail = memberEmail("receiver")
        val AMOUNT = MoneyAmount.ofCents(1_200)
        val REIMBURSED_AT = Instant.parse("2026-10-02T09:00:00Z")
        val DECLARED_AT = Instant.parse("2026-10-02T10:00:00Z")
        val DECIDED_AT = Instant.parse("2026-10-02T11:00:00Z")
        val ATTACHED_AT = Instant.parse("2026-10-02T10:00:01Z")
        val DOWNLOAD_EXPIRES_AT = Instant.parse("2026-10-02T10:05:00Z")
        val DOWNLOAD_VALID_FOR: Duration = Duration.ofMinutes(7)
        val DOWNLOAD_URI: URI = URI.create("https://documents.example/receipt.pdf")
        val STORAGE_KEY: DocumentStorageKey = DocumentStorageKey.of("documents/$DOCUMENT")
        val FILE_NAME: DocumentFileName = DocumentFileName.of("receipt.pdf")
        val CHECKSUM: DocumentSha256 = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32)))
    }
}
