package tech.justdev.domain.document.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
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
import java.util.stream.Stream

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

        @ParameterizedTest(name = "{0}")
        @MethodSource("tech.justdev.domain.document.entity.DocumentUploadIntentTest#invalidExpiryCases")
        fun `should reject an expiry that is not after creation`(
            caseName: String,
            expiresAt: Instant,
        ) {
            val error = assertThrows<IllegalArgumentException> { pendingIntent(expiresAt = expiresAt) }

            assertEquals("document upload intent expiry must be after creation", error.message)
        }
    }

    @Nested
    inner class Restore {
        @ParameterizedTest(name = "{0}")
        @MethodSource("tech.justdev.domain.document.entity.DocumentUploadIntentTest#coherentStatuses")
        fun `should restore every coherent persisted state`(
            caseName: String,
            status: DocumentUploadIntentStatus,
        ) {
            assertEquals(status, restoredIntent(status).status)
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("tech.justdev.domain.document.entity.DocumentUploadIntentTest#invalidVerificationTimes")
        fun `should reject a ready timestamp outside the intent lifetime`(
            caseName: String,
            readyAt: Instant,
        ) {
            val error =
                assertThrows<IllegalArgumentException> {
                    restoredIntent(DocumentUploadIntentStatus.Ready(readyAt))
                }

            assertEquals("document verification must occur during the upload intent lifetime", error.message)
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("tech.justdev.domain.document.entity.DocumentUploadIntentTest#invalidConsumedStatuses")
        fun `should reject incoherent consumed timestamps`(
            caseName: String,
            status: DocumentUploadIntentStatus.Consumed,
            expectedMessage: String,
        ) {
            val error = assertThrows<IllegalArgumentException> { restoredIntent(status) }

            assertEquals(expectedMessage, error.message)
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

        @ParameterizedTest(name = "{0}")
        @MethodSource("tech.justdev.domain.document.entity.DocumentUploadIntentTest#mismatchedMetadataCases")
        fun `should reject stored metadata that differs from expectations`(
            caseName: String,
            metadata: DocumentMetadata,
        ) {
            val error =
                assertThrows<IllegalArgumentException> {
                    pendingIntent().markReady(metadata, Instant.parse("2026-08-16T10:02:00Z"))
                }

            assertEquals("stored document metadata does not match upload intent", error.message)
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("tech.justdev.domain.document.entity.DocumentUploadIntentTest#invalidMarkReadyTimes")
        fun `should reject verification outside the intent lifetime`(
            caseName: String,
            readyAt: Instant,
        ) {
            val error = assertThrows<IllegalArgumentException> { pendingIntent().markReady(EXPECTED_METADATA, readyAt) }

            assertEquals("document verification must occur during the upload intent lifetime", error.message)
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

        @ParameterizedTest(name = "{0}")
        @MethodSource("tech.justdev.domain.document.entity.DocumentUploadIntentTest#nonReadyStatuses")
        fun `should reject consumption unless the intent is ready`(
            caseName: String,
            status: DocumentUploadIntentStatus,
        ) {
            val error =
                assertThrows<IllegalArgumentException> {
                    restoredIntent(status).consume(Instant.parse("2026-08-16T10:04:00Z"))
                }

            assertEquals("only a ready document upload intent can be consumed", error.message)
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("tech.justdev.domain.document.entity.DocumentUploadIntentTest#invalidConsumptionTimes")
        fun `should reject consumption before verification or after expiry`(
            caseName: String,
            consumedAt: Instant,
            expectedMessage: String,
        ) {
            val readyAt = Instant.parse("2026-08-16T10:02:00Z")
            val ready = pendingIntent().markReady(EXPECTED_METADATA, readyAt)

            val error = assertThrows<IllegalArgumentException> { ready.consume(consumedAt) }

            assertEquals(expectedMessage, error.message)
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

    private fun restoredIntent(status: DocumentUploadIntentStatus): DocumentUploadIntent =
        DocumentUploadIntent.restore(
            id = DocumentUploadIntentId(testUuid("document-upload-intent")),
            group = groupId("documents"),
            uploader = memberEmail("uploader"),
            storageKey = KEY,
            fileName = FILE_NAME,
            expectedMetadata = EXPECTED_METADATA,
            createdAt = CREATED_AT,
            expiresAt = EXPIRES_AT,
            status = status,
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

        @JvmStatic
        fun invalidExpiryCases(): Stream<Arguments> =
            Stream.of(
                Arguments.of("at creation", CREATED_AT),
                Arguments.of("before creation", CREATED_AT.minusSeconds(1)),
            )

        @JvmStatic
        fun coherentStatuses(): Stream<Arguments> {
            val readyAt = Instant.parse("2026-08-16T10:02:00Z")
            val consumedAt = Instant.parse("2026-08-16T10:03:00Z")

            return Stream.of(
                Arguments.of("pending", DocumentUploadIntentStatus.Pending),
                Arguments.of("ready", DocumentUploadIntentStatus.Ready(readyAt)),
                Arguments.of("consumed", DocumentUploadIntentStatus.Consumed(readyAt, consumedAt)),
            )
        }

        @JvmStatic
        fun invalidVerificationTimes(): Stream<Arguments> =
            Stream.of(
                Arguments.of("before creation", CREATED_AT.minusNanos(1)),
                Arguments.of("at expiry", EXPIRES_AT),
            )

        @JvmStatic
        fun invalidConsumedStatuses(): Stream<Arguments> {
            val readyAt = Instant.parse("2026-08-16T10:02:00Z")

            return Stream.of(
                Arguments.of(
                    "before verification",
                    DocumentUploadIntentStatus.Consumed(readyAt, readyAt.minusNanos(1)),
                    "document consumption must not precede verification",
                ),
                Arguments.of(
                    "at expiry",
                    DocumentUploadIntentStatus.Consumed(readyAt, EXPIRES_AT),
                    "document consumption must occur during the upload intent lifetime",
                ),
            )
        }

        @JvmStatic
        fun mismatchedMetadataCases(): Stream<Arguments> =
            Stream.of(
                Arguments.of("media type", EXPECTED_METADATA.copy(mediaType = DocumentMediaType.of("image/png"))),
                Arguments.of("size", EXPECTED_METADATA.copy(size = DocumentSize.ofBytes(513))),
                Arguments.of("checksum", EXPECTED_METADATA.copy(checksum = sha256(1))),
            )

        @JvmStatic
        fun invalidMarkReadyTimes(): Stream<Arguments> =
            Stream.of(
                Arguments.of("before creation", CREATED_AT.minusNanos(1)),
                Arguments.of("at expiry", EXPIRES_AT),
                Arguments.of("after expiry", EXPIRES_AT.plusNanos(1)),
            )

        @JvmStatic
        fun nonReadyStatuses(): Stream<Arguments> {
            val readyAt = Instant.parse("2026-08-16T10:02:00Z")
            val consumedAt = Instant.parse("2026-08-16T10:03:00Z")

            return Stream.of(
                Arguments.of("pending", DocumentUploadIntentStatus.Pending),
                Arguments.of("consumed", DocumentUploadIntentStatus.Consumed(readyAt, consumedAt)),
            )
        }

        @JvmStatic
        fun invalidConsumptionTimes(): Stream<Arguments> {
            val readyAt = Instant.parse("2026-08-16T10:02:00Z")

            return Stream.of(
                Arguments.of(
                    "before verification",
                    readyAt.minusNanos(1),
                    "document consumption must not precede verification",
                ),
                Arguments.of(
                    "at expiry",
                    EXPIRES_AT,
                    "document consumption must occur during the upload intent lifetime",
                ),
                Arguments.of(
                    "after expiry",
                    EXPIRES_AT.plusNanos(1),
                    "document consumption must occur during the upload intent lifetime",
                ),
            )
        }
    }
}
