package tech.justdev.application.document

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.domain.document.valueobject.DocumentFileName
import tech.justdev.domain.document.valueobject.DocumentMediaType
import tech.justdev.domain.document.valueobject.DocumentSha256
import tech.justdev.domain.document.valueobject.DocumentSize
import tech.justdev.domain.document.valueobject.DocumentStorageKey
import java.time.Duration
import java.util.Base64

class DocumentStorageTest {
    @Nested
    inner class DocumentUploadRequestConstruction {
        @Test
        fun `should preserve a positive validity duration`() {
            val request =
                DocumentUploadRequest(
                    key = DocumentStorageKey.of("documents/invoice.pdf"),
                    mediaType = DocumentMediaType.of("application/pdf"),
                    size = DocumentSize.ofBytes(128),
                    checksum = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32))),
                    validFor = Duration.ofMinutes(5),
                )

            assertEquals(Duration.ofMinutes(5), request.validFor)
        }

        @Test
        fun `should reject a non positive validity duration`() {
            listOf(Duration.ZERO, Duration.ofSeconds(-1)).forEach { validFor ->
                val error = assertThrows<IllegalArgumentException> { uploadRequest(validFor) }

                assertEquals("document upload validity duration must be strictly positive", error.message)
            }
        }
    }

    @Nested
    inner class DocumentDownloadRequestConstruction {
        @Test
        fun `should preserve a positive validity duration`() {
            val request = downloadRequest(Duration.ofMinutes(5))

            assertEquals(Duration.ofMinutes(5), request.validFor)
        }

        @Test
        fun `should reject a non positive validity duration`() {
            listOf(Duration.ZERO, Duration.ofSeconds(-1)).forEach { validFor ->
                val error = assertThrows<IllegalArgumentException> { downloadRequest(validFor) }

                assertEquals("document download validity duration must be strictly positive", error.message)
            }
        }
    }

    private fun uploadRequest(validFor: Duration): DocumentUploadRequest =
        DocumentUploadRequest(
            key = DocumentStorageKey.of("documents/invoice.pdf"),
            mediaType = DocumentMediaType.of("application/pdf"),
            size = DocumentSize.ofBytes(128),
            checksum = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32))),
            validFor = validFor,
        )

    private fun downloadRequest(validFor: Duration): DocumentDownloadRequest =
        DocumentDownloadRequest(
            key = DocumentStorageKey.of("documents/invoice.pdf"),
            fileName = DocumentFileName.of("invoice.pdf"),
            validFor = validFor,
        )
}
