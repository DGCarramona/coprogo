package tech.justdev.domain.reimbursement.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import tech.justdev.domain.document.entity.DocumentMetadata
import tech.justdev.domain.document.entity.DocumentUploadIntent
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

class ReimbursementSupportingDocumentTest {
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class FromConsumedUploadIntent {
        @Test
        fun `should retain a self-contained snapshot of a consumed upload intent`() {
            val intent = consumedIntent()

            val document = ReimbursementSupportingDocument.fromConsumedUploadIntent(intent)

            assertEquals(intent.id, document.sourceUploadIntent)
            assertEquals(intent.group, document.group)
            assertEquals(intent.uploader, document.uploader)
            assertEquals(intent.storageKey, document.storageKey)
            assertEquals(intent.fileName, document.fileName)
            assertEquals(intent.expectedMetadata, document.metadata)
            assertEquals(CONSUMED_AT, document.attachedAt)
        }

        @ParameterizedTest
        @MethodSource("unconsumedIntents")
        fun `should reject an upload intent that is not consumed`(intent: DocumentUploadIntent) {
            val error =
                assertThrows<IllegalArgumentException> {
                    ReimbursementSupportingDocument.fromConsumedUploadIntent(intent)
                }

            assertEquals("documented reimbursement supporting document requires a consumed upload intent", error.message)
        }

        private fun unconsumedIntents() =
            listOf(
                pendingIntent(),
                pendingIntent().markReady(METADATA, READY_AT),
            )
    }

    private fun pendingIntent(): DocumentUploadIntent =
        DocumentUploadIntent.create(
            id = DocumentUploadIntentId(testUuid("reimbursement-document")),
            group = GROUP,
            uploader = memberEmail("bob"),
            storageKey = DocumentStorageKey.of("groups/reimbursements/documents/reimbursement.pdf"),
            fileName = DocumentFileName.of("reimbursement.pdf"),
            expectedMetadata = METADATA,
            createdAt = CREATED_AT,
            expiresAt = CREATED_AT.plusSeconds(600),
        )

    private fun consumedIntent(): DocumentUploadIntent = pendingIntent().markReady(METADATA, READY_AT).consume(CONSUMED_AT)

    private companion object {
        val GROUP = groupId("reimbursements")
        val CREATED_AT: Instant = Instant.parse("2026-09-27T17:00:00Z")
        val READY_AT: Instant = CREATED_AT.plusSeconds(10)
        val CONSUMED_AT: Instant = CREATED_AT.plusSeconds(20)
        val METADATA =
            DocumentMetadata(
                mediaType = DocumentMediaType.of("application/pdf"),
                size = DocumentSize.ofBytes(128),
                checksum = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32))),
            )
    }
}
