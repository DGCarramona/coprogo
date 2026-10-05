package tech.justdev.interfaces.document

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tech.justdev.application.document.DocumentDownloadRequest
import tech.justdev.application.document.DocumentDownloadTarget
import tech.justdev.application.document.DocumentStorage
import tech.justdev.application.document.DocumentUploadRequest
import tech.justdev.application.document.DocumentUploadTarget
import tech.justdev.application.document.ExpenseSupportingDocumentAuditAction
import tech.justdev.application.document.ExpenseSupportingDocumentAuditEntrySnapshot
import tech.justdev.application.document.ListExpenseSupportingDocumentsResult
import tech.justdev.application.document.ListedExpenseSupportingDocument
import tech.justdev.domain.document.entity.DocumentMetadata
import tech.justdev.domain.document.entity.ExpenseSupportingDocument
import tech.justdev.domain.document.valueobject.DocumentFileName
import tech.justdev.domain.document.valueobject.DocumentMediaType
import tech.justdev.domain.document.valueobject.DocumentSha256
import tech.justdev.domain.document.valueobject.DocumentSize
import tech.justdev.domain.document.valueobject.DocumentStorageKey
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.document.valueobject.SupportingDocumentAttachmentDeletion
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.interfaces.configuration.SupportingDocumentDownloadConfiguration
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

class ExpenseSupportingDocumentPresenterTest {
    @Nested
    inner class Present {
        @Test
        fun `should map current and history and presign every historical document once`() =
            runTest {
                val current = document("current", "current.pdf", CURRENT_ATTACHED_AT)
                val historical =
                    document(
                        id = "historical",
                        fileName = "historical.jpg",
                        attachedAt = HISTORICAL_ATTACHED_AT,
                        deletion = SupportingDocumentAttachmentDeletion(DELETER, DELETED_AT),
                    )
                val storage = RecordingDocumentStorage()
                val presenter = presenter(storage, Duration.ofSeconds(42))

                val response =
                    presenter.present(
                        ListExpenseSupportingDocumentsResult(
                            current = listOf(ListedExpenseSupportingDocument(current, canDelete = true)),
                            history =
                                listOf(
                                    ListedExpenseSupportingDocument(historical, canDelete = false),
                                    ListedExpenseSupportingDocument(current, canDelete = true),
                                ),
                        ),
                    )

                assertEquals(
                    ExpenseSupportingDocumentsResponse(
                        current = listOf(expectedResponse(current, canDelete = true)),
                        history =
                            listOf(
                                expectedResponse(historical, canDelete = false),
                                expectedResponse(current, canDelete = true),
                            ),
                    ),
                    response,
                )
                assertEquals(
                    listOf(
                        DocumentDownloadRequest(historical.storageKey, historical.fileName, Duration.ofSeconds(42)),
                        DocumentDownloadRequest(current.storageKey, current.fileName, Duration.ofSeconds(42)),
                    ),
                    storage.downloadRequests,
                )
            }

        @Test
        fun `should return empty current and history without accessing storage`() =
            runTest {
                val storage = RecordingDocumentStorage()

                val response = presenter(storage).present(ListExpenseSupportingDocumentsResult(emptyList(), emptyList()))

                assertEquals(ExpenseSupportingDocumentsResponse(emptyList(), emptyList()), response)
                assertEquals(emptyList<DocumentDownloadRequest>(), storage.downloadRequests)
            }
    }

    @Nested
    inner class PresentAuditTrail {
        @Test
        fun `should map every audit action`() {
            val entries =
                listOf(
                    auditEntry(ExpenseSupportingDocumentAuditAction.ATTACHED, "attached", null),
                    auditEntry(
                        ExpenseSupportingDocumentAuditAction.REPLACED,
                        "replacement",
                        UUID.nameUUIDFromBytes("replaced".toByteArray()),
                    ),
                    auditEntry(ExpenseSupportingDocumentAuditAction.DELETED, "deleted", null),
                )

            val response = presenter(RecordingDocumentStorage()).presentAuditTrail(entries)

            assertEquals(
                entries.map { entry ->
                    ExpenseSupportingDocumentAuditEntryResponse(
                        action = ExpenseSupportingDocumentAuditActionResponse.valueOf(entry.action.name),
                        occurredAt = entry.occurredAt,
                        performedBy = entry.performedBy.toPrimitive(),
                        documentUploadIntent = entry.documentUploadIntent.toPrimitive(),
                        replacedDocumentUploadIntent = entry.replacedDocumentUploadIntent?.toPrimitive(),
                        fileName = entry.fileName.toPrimitive(),
                    )
                },
                response,
            )
        }
    }

    private fun presenter(
        storage: DocumentStorage,
        validity: Duration = Duration.ofMinutes(5),
    ): ExpenseSupportingDocumentPresenter =
        ExpenseSupportingDocumentPresenter(
            documentStorage = storage,
            configuration = SupportingDocumentDownloadConfiguration().apply { validFor = validity },
        )

    private fun document(
        id: String,
        fileName: String,
        attachedAt: Instant,
        deletion: SupportingDocumentAttachmentDeletion? = null,
    ): ExpenseSupportingDocument =
        ExpenseSupportingDocument.restore(
            sourceUploadIntent = intent(id),
            uploader = UPLOADER,
            storageKey = DocumentStorageKey.of("documents/$id"),
            fileName = DocumentFileName.of(fileName),
            metadata =
                DocumentMetadata(
                    mediaType = DocumentMediaType.of(if (fileName.endsWith(".pdf")) "application/pdf" else "image/jpeg"),
                    size = DocumentSize.ofBytes(1_234),
                    checksum = CHECKSUM,
                ),
            attachedAt = attachedAt,
            deletion = deletion,
        )

    private fun expectedResponse(
        document: ExpenseSupportingDocument,
        canDelete: Boolean,
    ): ExpenseSupportingDocumentResponse =
        ExpenseSupportingDocumentResponse(
            sourceUploadIntent = document.sourceUploadIntent.toPrimitive(),
            fileName = document.fileName.toPrimitive(),
            mediaType = document.metadata.mediaType.toPrimitive(),
            sizeBytes = document.metadata.size.toBytes(),
            uploader = document.uploader.toPrimitive(),
            attachedAt = document.attachedAt,
            replacesSourceUploadIntent = null,
            deletion = document.deletion?.let { SupportingDocumentDeletionResponse(it.deletedBy.toPrimitive(), it.deletedAt) },
            canDelete = canDelete,
            download =
                SupportingDocumentDownloadResponse(
                    URI.create("https://storage.example.test/${document.storageKey.toPrimitive()}"),
                    DOWNLOAD_EXPIRES_AT,
                ),
        )

    private fun auditEntry(
        action: ExpenseSupportingDocumentAuditAction,
        id: String,
        replaced: UUID?,
    ): ExpenseSupportingDocumentAuditEntrySnapshot =
        ExpenseSupportingDocumentAuditEntrySnapshot(
            action = action,
            occurredAt = CURRENT_ATTACHED_AT,
            performedBy = UPLOADER,
            documentUploadIntent = intent(id),
            replacedDocumentUploadIntent = replaced?.let(::DocumentUploadIntentId),
            fileName = DocumentFileName.of("invoice.pdf"),
        )

    private fun intent(value: String): DocumentUploadIntentId = DocumentUploadIntentId(UUID.nameUUIDFromBytes(value.toByteArray()))

    private class RecordingDocumentStorage : DocumentStorage {
        val downloadRequests = mutableListOf<DocumentDownloadRequest>()

        override suspend fun presignUpload(request: DocumentUploadRequest): DocumentUploadTarget = error("not used")

        override suspend fun inspect(key: DocumentStorageKey): DocumentMetadata? = error("not used")

        override suspend fun presignDownload(request: DocumentDownloadRequest): DocumentDownloadTarget {
            downloadRequests += request
            return DocumentDownloadTarget(
                uri = URI.create("https://storage.example.test/${request.key.toPrimitive()}"),
                expiresAt = DOWNLOAD_EXPIRES_AT,
            )
        }
    }

    private companion object {
        val UPLOADER: MemberEmail = MemberEmail.of("uploader@example.com")
        val DELETER: MemberEmail = MemberEmail.of("deleter@example.com")
        val HISTORICAL_ATTACHED_AT: Instant = Instant.parse("2026-09-23T09:00:00Z")
        val CURRENT_ATTACHED_AT: Instant = Instant.parse("2026-09-23T10:00:00Z")
        val DELETED_AT: Instant = Instant.parse("2026-09-23T11:00:00Z")
        val DOWNLOAD_EXPIRES_AT: Instant = Instant.parse("2026-09-23T12:00:00Z")
        val CHECKSUM: DocumentSha256 = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32)))
    }
}
