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
import tech.justdev.domain.document.valueobject.SupportingDocumentAttachmentDeletion
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.testUuid
import java.time.Instant
import java.util.Base64

class ExpenseSupportingDocumentTest {
    @Nested
    inner class FromConsumedUploadIntent {
        @Test
        fun `should snapshot a consumed upload intent`() {
            val intent = consumedIntent()

            val document = ExpenseSupportingDocument.fromConsumedUploadIntent(intent)

            assertEquals(intent.id, document.sourceUploadIntent)
            assertEquals(intent.storageKey, document.storageKey)
            assertEquals(intent.fileName, document.fileName)
            assertEquals(intent.expectedMetadata, document.metadata)
            assertEquals(intent.uploader, document.uploader)
            assertEquals(CONSUMED_AT, document.attachedAt)
            assertEquals(null, document.replacesSourceUploadIntent)
            assertEquals(null, document.deletion)
        }

        @Test
        fun `should reject a source upload intent that is not consumed`() {
            val errors =
                listOf(
                    pendingIntent(),
                    pendingIntent("ready").markReady(METADATA, READY_AT),
                ).map { intent ->
                    assertThrows<IllegalArgumentException> { ExpenseSupportingDocument.fromConsumedUploadIntent(intent) }.message
                }

            assertEquals(
                List(2) { "expense supporting document requires a consumed upload intent" },
                errors,
            )
        }
    }

    @Nested
    inner class ReplaceWith {
        @Test
        fun `should retain the source snapshot when replacing a document`() {
            val document = ExpenseSupportingDocument.fromConsumedUploadIntent(consumedIntent("original"))
            val replacement = document.replaceWith(consumedIntent("replacement"))

            assertEquals(document.sourceUploadIntent, replacement.replacesSourceUploadIntent)
            assertEquals("original.pdf", document.fileName.toPrimitive())
            assertEquals("replacement.pdf", replacement.fileName.toPrimitive())
        }

        @Test
        fun `should reject a replacement consumed before the source attachment`() {
            val document = ExpenseSupportingDocument.fromConsumedUploadIntent(consumedIntent("original"))

            val error =
                assertThrows<IllegalArgumentException> {
                    document.replaceWith(consumedIntent("replacement", consumedAt = CONSUMED_AT.minusSeconds(1)))
                }

            assertEquals("supporting document replacement must not precede the source attachment", error.message)
        }

        @Test
        fun `should reject replacement of a deleted document`() {
            val deleted = ExpenseSupportingDocument.fromConsumedUploadIntent(consumedIntent()).delete(memberEmail("deleter"), DELETED_AT)

            val error = assertThrows<IllegalArgumentException> { deleted.replaceWith(consumedIntent("replacement")) }

            assertEquals("deleted supporting document cannot be replaced", error.message)
        }
    }

    @Nested
    inner class Delete {
        @Test
        fun `should retain source upload metadata in the deletion tombstone`() {
            val document = ExpenseSupportingDocument.fromConsumedUploadIntent(consumedIntent())

            val deleted = document.delete(memberEmail("deleter"), DELETED_AT)

            assertEquals(document.sourceUploadIntent, deleted.sourceUploadIntent)
            assertEquals(SupportingDocumentAttachmentDeletion(memberEmail("deleter"), DELETED_AT), deleted.deletion)
        }

        @Test
        fun `should reject a deletion before the attachment`() {
            val document = ExpenseSupportingDocument.fromConsumedUploadIntent(consumedIntent())

            val error =
                assertThrows<IllegalArgumentException> {
                    document.delete(memberEmail("deleter"), CONSUMED_AT.minusSeconds(1))
                }

            assertEquals("supporting document deletion must not precede its attachment", error.message)
        }
    }

    private fun pendingIntent(seed: String = "source"): DocumentUploadIntent =
        DocumentUploadIntent.create(
            id = DocumentUploadIntentId(testUuid(seed)),
            group = GROUP,
            uploader = memberEmail("uploader"),
            storageKey = DocumentStorageKey.of("groups/documents/$seed.pdf"),
            fileName = DocumentFileName.of("$seed.pdf"),
            expectedMetadata = METADATA,
            createdAt = CREATED_AT,
            expiresAt = CREATED_AT.plusSeconds(600),
        )

    private fun consumedIntent(
        seed: String = "source",
        consumedAt: Instant = CONSUMED_AT,
    ): DocumentUploadIntent = pendingIntent(seed).markReady(METADATA, READY_AT).consume(consumedAt)

    private companion object {
        val GROUP = groupId("documents")
        val CREATED_AT: Instant = Instant.parse("2026-05-01T10:00:00Z")
        val READY_AT: Instant = CREATED_AT.plusSeconds(10)
        val CONSUMED_AT: Instant = CREATED_AT.plusSeconds(20)
        val DELETED_AT: Instant = CREATED_AT.plusSeconds(30)
        val METADATA =
            DocumentMetadata(
                mediaType = DocumentMediaType.of("application/pdf"),
                size = DocumentSize.ofBytes(256),
                checksum = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32) { 7 })),
            )
    }
}
