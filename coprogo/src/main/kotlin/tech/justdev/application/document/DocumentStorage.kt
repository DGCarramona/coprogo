package tech.justdev.application.document

import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Locale

@JvmInline
value class DocumentStorageKey private constructor(
    private val value: String,
) {
    fun toPrimitive(): String = value

    companion object {
        fun of(value: String): DocumentStorageKey {
            require(value.isNotBlank()) { "document storage key must not be blank" }
            return DocumentStorageKey(value)
        }
    }
}

@JvmInline
value class DocumentMediaType private constructor(
    private val value: String,
) {
    fun toPrimitive(): String = value

    companion object {
        private val simpleMediaType = Regex("[a-z0-9][a-z0-9!#$&^_.+-]*/[a-z0-9][a-z0-9!#$&^_.+-]*")

        fun of(value: String): DocumentMediaType {
            val normalized = value.trim().lowercase(Locale.ROOT)
            require(simpleMediaType.matches(normalized)) { "document media type must contain a simple type and subtype" }
            return DocumentMediaType(normalized)
        }
    }
}

@JvmInline
value class DocumentSize private constructor(
    private val bytes: Long,
) {
    fun toBytes(): Long = bytes

    companion object {
        fun ofBytes(bytes: Long): DocumentSize {
            require(bytes > 0) { "document size must be strictly positive" }
            return DocumentSize(bytes)
        }
    }
}

@JvmInline
value class DocumentSha256 private constructor(
    private val base64: String,
) {
    fun toBase64(): String = base64

    companion object {
        fun fromBase64(value: String): DocumentSha256 {
            val decoded = runCatching { Base64.getDecoder().decode(value) }.getOrNull()
            val isCanonical = decoded?.let { bytes -> bytes.size == 32 && Base64.getEncoder().encodeToString(bytes) == value } == true
            require(isCanonical) { "document sha256 must be canonical base64 encoding exactly 32 bytes" }
            return DocumentSha256(value)
        }
    }
}

@JvmInline
value class DocumentFileName private constructor(
    private val value: String,
) {
    fun toPrimitive(): String = value

    companion object {
        fun of(value: String): DocumentFileName {
            val normalized = value.trim()
            require(normalized.isNotEmpty()) { "document file name must not be blank" }
            require(normalized.length <= 255) { "document file name must not exceed 255 characters" }
            require(normalized.none(Char::isISOControl)) { "document file name must not contain control characters" }
            require('/' !in normalized && '\\' !in normalized) { "document file name must not contain path separators" }
            return DocumentFileName(normalized)
        }
    }
}

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

data class StoredDocumentMetadata(
    val mediaType: DocumentMediaType,
    val size: DocumentSize,
    val checksum: DocumentSha256,
)

interface DocumentStorage {
    /**
     * The target must bind the media type and checksum and require that the key does not already exist.
     */
    suspend fun presignUpload(request: DocumentUploadRequest): DocumentUploadTarget

    suspend fun inspect(key: DocumentStorageKey): StoredDocumentMetadata?

    suspend fun presignDownload(request: DocumentDownloadRequest): DocumentDownloadTarget
}
