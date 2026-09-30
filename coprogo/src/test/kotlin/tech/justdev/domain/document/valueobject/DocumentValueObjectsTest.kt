package tech.justdev.domain.document.valueobject

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.util.Base64
import java.util.stream.Stream

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

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = ["", "application", "/pdf", "image/", "image/svg/xml", "text / plain", "text/plain; charset=utf-8"])
        fun `should reject a media type without a simple type and subtype`(value: String) {
            assertThrows<IllegalArgumentException>(value) { DocumentMediaType.of(value) }
        }
    }

    @Nested
    inner class DocumentSizeOfBytes {
        @Test
        fun `should preserve a positive byte count`() {
            assertEquals(42L, DocumentSize.ofBytes(42).toBytes())
        }

        @ParameterizedTest(name = "[{index}] {0} bytes")
        @ValueSource(longs = [0L, -1L])
        fun `should reject zero or negative byte counts`(bytes: Long) {
            val error = assertThrows<IllegalArgumentException> { DocumentSize.ofBytes(bytes) }

            assertEquals("document size must be strictly positive", error.message)
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

        @ParameterizedTest(name = "{0}")
        @MethodSource("tech.justdev.domain.document.valueobject.DocumentValueObjectsTest#invalidSha256Checksums")
        fun `should reject malformed non canonical or incorrectly sized checksums`(
            caseName: String,
            value: String,
        ) {
            val error = assertThrows<IllegalArgumentException>(caseName) { DocumentSha256.fromBase64(value) }

            assertEquals("document sha256 must be canonical base64 encoding exactly 32 bytes", error.message)
        }
    }

    @Nested
    inner class DocumentFileNameOf {
        @Test
        fun `should trim and preserve a reasonable unicode file name`() {
            assertEquals("Facture été 2026.pdf", DocumentFileName.of("  Facture été 2026.pdf  ").toPrimitive())
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("tech.justdev.domain.document.valueobject.DocumentValueObjectsTest#invalidFileNames")
        fun `should reject blank overly long controlled or path file names`(
            caseName: String,
            value: String,
        ) {
            assertThrows<IllegalArgumentException>(caseName) { DocumentFileName.of(value) }
        }
    }

    private companion object {
        @JvmStatic
        fun invalidSha256Checksums(): Stream<Arguments> {
            val encoded = Base64.getEncoder().encodeToString(ByteArray(32) { index -> index.toByte() })

            return Stream.of(
                Arguments.of("malformed", "not-base64"),
                Arguments.of("non canonical", encoded.removeSuffix("=")),
                Arguments.of("31 bytes", Base64.getEncoder().encodeToString(ByteArray(31))),
                Arguments.of("33 bytes", Base64.getEncoder().encodeToString(ByteArray(33))),
            )
        }

        @JvmStatic
        fun invalidFileNames(): Stream<Arguments> =
            Stream.of(
                Arguments.of("blank", " "),
                Arguments.of("overly long", "a".repeat(256)),
                Arguments.of("controlled character", "invoice\u0000.pdf"),
                Arguments.of("forward slash path", "folder/invoice.pdf"),
                Arguments.of("backslash path", "folder\\invoice.pdf"),
            )
    }
}
