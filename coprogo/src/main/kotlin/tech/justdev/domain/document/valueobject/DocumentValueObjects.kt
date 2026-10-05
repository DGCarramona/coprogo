package tech.justdev.domain.document.valueobject

import java.util.Base64
import java.util.Locale
import java.util.UUID

@JvmInline
value class DocumentUploadIntentId(
    private val value: UUID,
) {
    fun toPrimitive(): UUID = value
}

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
