package tech.justdev.domain.document.entity

import tech.justdev.domain.document.valueobject.DocumentFileName
import tech.justdev.domain.document.valueobject.DocumentMediaType
import tech.justdev.domain.document.valueobject.DocumentSha256
import tech.justdev.domain.document.valueobject.DocumentSize
import tech.justdev.domain.document.valueobject.DocumentStorageKey
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId
import java.time.Instant

data class DocumentMetadata(
    val mediaType: DocumentMediaType,
    val size: DocumentSize,
    val checksum: DocumentSha256,
)

sealed interface DocumentUploadIntentStatus {
    data object Pending : DocumentUploadIntentStatus

    data class Ready(
        val verifiedAt: Instant,
    ) : DocumentUploadIntentStatus

    data class Consumed(
        val verifiedAt: Instant,
        val consumedAt: Instant,
    ) : DocumentUploadIntentStatus
}

class DocumentUploadIntent private constructor(
    val id: DocumentUploadIntentId,
    val group: GroupId,
    val uploader: MemberEmail,
    val storageKey: DocumentStorageKey,
    val fileName: DocumentFileName,
    val expectedMetadata: DocumentMetadata,
    val createdAt: Instant,
    val expiresAt: Instant,
    val status: DocumentUploadIntentStatus,
) {
    fun markReady(
        metadata: DocumentMetadata,
        verifiedAt: Instant,
    ): DocumentUploadIntent {
        require(status is DocumentUploadIntentStatus.Pending) {
            "only a pending document upload intent can become ready"
        }
        require(verifiedAt >= createdAt && verifiedAt < expiresAt) {
            "document verification must occur during the upload intent lifetime"
        }
        require(metadata == expectedMetadata) {
            "stored document metadata does not match upload intent"
        }

        return withStatus(DocumentUploadIntentStatus.Ready(verifiedAt))
    }

    fun consume(consumedAt: Instant): DocumentUploadIntent {
        require(status is DocumentUploadIntentStatus.Ready) {
            "only a ready document upload intent can be consumed"
        }
        require(consumedAt >= status.verifiedAt) { "document consumption must not precede verification" }
        require(consumedAt < expiresAt) { "document consumption must occur during the upload intent lifetime" }

        return withStatus(
            DocumentUploadIntentStatus.Consumed(
                verifiedAt = status.verifiedAt,
                consumedAt = consumedAt,
            ),
        )
    }

    private fun withStatus(status: DocumentUploadIntentStatus): DocumentUploadIntent =
        DocumentUploadIntent(
            id = id,
            group = group,
            uploader = uploader,
            storageKey = storageKey,
            fileName = fileName,
            expectedMetadata = expectedMetadata,
            createdAt = createdAt,
            expiresAt = expiresAt,
            status = status,
        )

    companion object {
        fun create(
            id: DocumentUploadIntentId,
            group: GroupId,
            uploader: MemberEmail,
            storageKey: DocumentStorageKey,
            fileName: DocumentFileName,
            expectedMetadata: DocumentMetadata,
            createdAt: Instant,
            expiresAt: Instant,
        ): DocumentUploadIntent {
            require(expiresAt > createdAt) { "document upload intent expiry must be after creation" }

            return DocumentUploadIntent(
                id = id,
                group = group,
                uploader = uploader,
                storageKey = storageKey,
                fileName = fileName,
                expectedMetadata = expectedMetadata,
                createdAt = createdAt,
                expiresAt = expiresAt,
                status = DocumentUploadIntentStatus.Pending,
            )
        }
    }
}
