package tech.justdev.domain.document.valueobject

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Base64

class DocumentValueObjectsTest {
    @Nested
    inner class DocumentStorageKeyOf {
        @Test
        fun `should preserve a valid key`() {
            val value = "expenses/2026/invoice.pdf"

            assertEquals(value, DocumentStorageKey.of(value).toPrimitive())
        }

        @Test
        fun `should reject a blank key`() {
            val error = assertThrows<IllegalArgumentException> { DocumentStorageKey.of("  ") }

            assertEquals("document storage key must not be blank", error.message)
        }
    }

    @Nested
    inner class DocumentMediaTypeOf {
        @Test
        fun `should trim and normalize a simple media type`() {
            assertEquals("application/vnd.coprogo+json", DocumentMediaType.of(" Application/Vnd.Coprogo+JSON ").toPrimitive())
        }

        @Test
        fun `should reject a media type without a simple type and subtype`() {
            listOf("", "application", "/pdf", "image/", "image/svg/xml", "text / plain", "text/plain; charset=utf-8")
                .forEach { value ->
                    assertThrows<IllegalArgumentException>(value) { DocumentMediaType.of(value) }
                }
        }
    }

    @Nested
    inner class DocumentSizeOfBytes {
        @Test
        fun `should preserve a positive byte count`() {
            assertEquals(42L, DocumentSize.ofBytes(42).toBytes())
        }

        @Test
        fun `should reject zero or negative byte counts`() {
            listOf(0L, -1L).forEach { bytes ->
                val error = assertThrows<IllegalArgumentException> { DocumentSize.ofBytes(bytes) }

                assertEquals("document size must be strictly positive", error.message)
            }
        }
    }

    @Nested
    inner class DocumentSha256FromBase64 {
        private val bytes = ByteArray(32) { index -> index.toByte() }
        private val encoded = Base64.getEncoder().encodeToString(bytes)

        @Test
        fun `should preserve a canonical sha256 checksum`() {
            assertEquals(encoded, DocumentSha256.fromBase64(encoded).toBase64())
        }

        @Test
        fun `should reject malformed non canonical or incorrectly sized checksums`() {
            listOf(
                "not-base64",
                encoded.removeSuffix("="),
                Base64.getEncoder().encodeToString(ByteArray(31)),
                Base64.getEncoder().encodeToString(ByteArray(33)),
            ).forEach { value ->
                val error = assertThrows<IllegalArgumentException>(value) { DocumentSha256.fromBase64(value) }

                assertEquals("document sha256 must be canonical base64 encoding exactly 32 bytes", error.message)
            }
        }
    }

    @Nested
    inner class DocumentFileNameOf {
        @Test
        fun `should trim and preserve a reasonable unicode file name`() {
            assertEquals("Facture été 2026.pdf", DocumentFileName.of("  Facture été 2026.pdf  ").toPrimitive())
        }

        @Test
        fun `should reject blank overly long controlled or path file names`() {
            listOf(" ", "a".repeat(256), "invoice\u0000.pdf", "folder/invoice.pdf", "folder\\invoice.pdf")
                .forEach { value ->
                    assertThrows<IllegalArgumentException>(value) { DocumentFileName.of(value) }
                }
        }
    }
}
