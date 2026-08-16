package tech.justdev.domain.document.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.domain.document.valueobject.DocumentFileName
import tech.justdev.domain.document.valueobject.DocumentMediaType
import tech.justdev.domain.document.valueobject.DocumentSha256
import tech.justdev.domain.document.valueobject.DocumentSize
import tech.justdev.domain.document.valueobject.DocumentStorageKey
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.testUuid
import java.time.Instant
import java.util.Base64

class DocumentUploadIntentTest {
    @Nested
    inner class Create {
        @Test
        fun `should create a pending upload intent with expected metadata`() {
            val intent = pendingIntent()

            assertEquals(DocumentUploadIntentId(testUuid("document-upload-intent")), intent.id)
            assertEquals(groupId("documents"), intent.group)
            assertEquals(memberEmail("uploader"), intent.uploader)
            assertEquals(KEY, intent.storageKey)
            assertEquals(FILE_NAME, intent.fileName)
            assertEquals(EXPECTED_METADATA, intent.expectedMetadata)
            assertEquals(CREATED_AT, intent.createdAt)
            assertEquals(EXPIRES_AT, intent.expiresAt)
            assertEquals(DocumentUploadIntentStatus.Pending, intent.status)
        }

        @Test
        fun `should reject an expiry that is not after creation`() {
            listOf(CREATED_AT, CREATED_AT.minusSeconds(1)).forEach { expiresAt ->
                val error = assertThrows<IllegalArgumentException> { pendingIntent(expiresAt = expiresAt) }

                assertEquals("document upload intent expiry must be after creation", error.message)
            }
        }
    }

    @Nested
    inner class MarkReady {
        @Test
        fun `should become ready when stored metadata exactly matches expectations`() {
            val readyAt = Instant.parse("2026-08-16T10:02:00Z")

            val ready = pendingIntent().markReady(EXPECTED_METADATA, readyAt)

            assertEquals(DocumentUploadIntentStatus.Ready(readyAt), ready.status)
        }

        @Test
        fun `should reject stored metadata that differs from expectations`() {
            listOf(
                EXPECTED_METADATA.copy(mediaType = DocumentMediaType.of("image/png")),
                EXPECTED_METADATA.copy(size = DocumentSize.ofBytes(513)),
                EXPECTED_METADATA.copy(checksum = sha256(1)),
            ).forEach { metadata ->
                val error =
                    assertThrows<IllegalArgumentException> {
                        pendingIntent().markReady(metadata, Instant.parse("2026-08-16T10:02:00Z"))
                    }

                assertEquals("stored document metadata does not match upload intent", error.message)
            }
        }

        @Test
        fun `should reject verification outside the intent lifetime`() {
            listOf(CREATED_AT.minusNanos(1), EXPIRES_AT, EXPIRES_AT.plusNanos(1)).forEach { readyAt ->
                val error = assertThrows<IllegalArgumentException> { pendingIntent().markReady(EXPECTED_METADATA, readyAt) }

                assertEquals("document verification must occur during the upload intent lifetime", error.message)
            }
        }

        @Test
        fun `should reject verification of a non pending intent`() {
            val ready = pendingIntent().markReady(EXPECTED_METADATA, Instant.parse("2026-08-16T10:02:00Z"))

            val error =
                assertThrows<IllegalArgumentException> {
                    ready.markReady(EXPECTED_METADATA, Instant.parse("2026-08-16T10:03:00Z"))
                }

            assertEquals("only a pending document upload intent can become ready", error.message)
        }
    }

    @Nested
    inner class Consume {
        @Test
        fun `should consume a ready upload intent`() {
            val readyAt = Instant.parse("2026-08-16T10:02:00Z")
            val consumedAt = Instant.parse("2026-08-16T10:03:00Z")
            val ready = pendingIntent().markReady(EXPECTED_METADATA, readyAt)

            val consumed = ready.consume(consumedAt)

            assertEquals(DocumentUploadIntentStatus.Consumed(readyAt, consumedAt), consumed.status)
        }

        @Test
        fun `should reject consumption unless the intent is ready`() {
            val consumedAt = Instant.parse("2026-08-16T10:03:00Z")
            val pendingError = assertThrows<IllegalArgumentException> { pendingIntent().consume(consumedAt) }
            val consumed =
                pendingIntent()
                    .markReady(EXPECTED_METADATA, Instant.parse("2026-08-16T10:02:00Z"))
                    .consume(consumedAt)

            val consumedError = assertThrows<IllegalArgumentException> { consumed.consume(consumedAt.plusSeconds(1)) }

            assertEquals("only a ready document upload intent can be consumed", pendingError.message)
            assertEquals("only a ready document upload intent can be consumed", consumedError.message)
        }

        @Test
        fun `should reject consumption before verification or after expiry`() {
            val readyAt = Instant.parse("2026-08-16T10:02:00Z")
            val ready = pendingIntent().markReady(EXPECTED_METADATA, readyAt)

            val beforeVerification = assertThrows<IllegalArgumentException> { ready.consume(readyAt.minusNanos(1)) }
            val atExpiry = assertThrows<IllegalArgumentException> { ready.consume(EXPIRES_AT) }
            val afterExpiry = assertThrows<IllegalArgumentException> { ready.consume(EXPIRES_AT.plusNanos(1)) }

            assertEquals("document consumption must not precede verification", beforeVerification.message)
            assertEquals("document consumption must occur during the upload intent lifetime", atExpiry.message)
            assertEquals("document consumption must occur during the upload intent lifetime", afterExpiry.message)
        }
    }

    private fun pendingIntent(expiresAt: Instant = EXPIRES_AT): DocumentUploadIntent =
        DocumentUploadIntent.create(
            id = DocumentUploadIntentId(testUuid("document-upload-intent")),
            group = groupId("documents"),
            uploader = memberEmail("uploader"),
            storageKey = KEY,
            fileName = FILE_NAME,
            expectedMetadata = EXPECTED_METADATA,
            createdAt = CREATED_AT,
            expiresAt = expiresAt,
        )

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-08-16T10:00:00Z")
        val EXPIRES_AT: Instant = Instant.parse("2026-08-16T10:05:00Z")
        val KEY: DocumentStorageKey = DocumentStorageKey.of("groups/documents/invoice.pdf")
        val FILE_NAME: DocumentFileName = DocumentFileName.of("Facture août 2026.pdf")
        val MEDIA_TYPE: DocumentMediaType = DocumentMediaType.of("application/pdf")
        val SIZE: DocumentSize = DocumentSize.ofBytes(512)
        val CHECKSUM: DocumentSha256 = sha256(0)
        val EXPECTED_METADATA = DocumentMetadata(MEDIA_TYPE, SIZE, CHECKSUM)

        fun sha256(fill: Byte): DocumentSha256 = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32) { fill }))
    }
}
