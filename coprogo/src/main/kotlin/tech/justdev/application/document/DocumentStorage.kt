package tech.justdev.application.document

import tech.justdev.domain.document.entity.DocumentMetadata
import tech.justdev.domain.document.valueobject.DocumentFileName
import tech.justdev.domain.document.valueobject.DocumentMediaType
import tech.justdev.domain.document.valueobject.DocumentSha256
import tech.justdev.domain.document.valueobject.DocumentSize
import tech.justdev.domain.document.valueobject.DocumentStorageKey
import java.net.URI
import java.time.Duration
import java.time.Instant

data class DocumentUploadRequest(
    val key: DocumentStorageKey,
    val mediaType: DocumentMediaType,
    val size: DocumentSize,
    val checksum: DocumentSha256,
    val validFor: Duration,
) {
    init {
        require(validFor > Duration.ZERO) { "document upload validity duration must be strictly positive" }
    }
}

data class DocumentDownloadRequest(
    val key: DocumentStorageKey,
    val fileName: DocumentFileName,
    val validFor: Duration,
) {
    init {
        require(validFor > Duration.ZERO) { "document download validity duration must be strictly positive" }
    }
}

data class DocumentUploadTarget(
    val uri: URI,
    val requiredHeaders: Map<String, String>,
    val expiresAt: Instant,
)

data class DocumentDownloadTarget(
    val uri: URI,
    val expiresAt: Instant,
)

interface DocumentStorage {
    /**
     * The target must bind the media type and checksum and require that the key does not already exist.
     */
    suspend fun presignUpload(request: DocumentUploadRequest): DocumentUploadTarget

    suspend fun inspect(key: DocumentStorageKey): DocumentMetadata?

    suspend fun presignDownload(request: DocumentDownloadRequest): DocumentDownloadTarget
}
