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
import tech.justdev.testsupport.expenseId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.testUuid
import java.time.Instant
import java.util.Base64

class ExpenseSupportingDocumentAttachmentTest {
    @Nested
    inner class Attach {
        @Test
        fun `should associate a consumed upload intent with an expense in its group`() {
            val intent = pendingIntent().markReady(METADATA, READY_AT).consume(CONSUMED_AT)

            val attachment =
                ExpenseSupportingDocumentAttachment.attach(
                    expense = expenseId("documented"),
                    group = intent.group,
                    intent = intent,
                )

            assertEquals(intent.id, attachment.sourceUploadIntent)
            assertEquals(intent.group, attachment.group)
            assertEquals(expenseId("documented"), attachment.expense)
        }

        @Test
        fun `should reject an upload intent that has not been consumed`() {
            val pending = pendingIntent()
            val ready = pending.markReady(METADATA, READY_AT)

            listOf(pending, ready).forEach { intent ->
                val error =
                    assertThrows<IllegalArgumentException> {
                        ExpenseSupportingDocumentAttachment.attach(
                            expense = expenseId("documented"),
                            group = intent.group,
                            intent = intent,
                        )
                    }

                assertEquals("expense supporting document requires a consumed upload intent", error.message)
            }
        }

        @Test
        fun `should reject an expense from another group`() {
            val intent = pendingIntent().markReady(METADATA, READY_AT).consume(CONSUMED_AT)

            val error =
                assertThrows<IllegalArgumentException> {
                    ExpenseSupportingDocumentAttachment.attach(
                        expense = expenseId("documented"),
                        group = groupId("other"),
                        intent = intent,
                    )
                }

            assertEquals("expense and supporting document must belong to the same group", error.message)
        }
    }

    private fun pendingIntent(): DocumentUploadIntent =
        DocumentUploadIntent.create(
            id = DocumentUploadIntentId(testUuid("support-document")),
            group = groupId("documents"),
            uploader = memberEmail("uploader"),
            storageKey = DocumentStorageKey.of("groups/documents/invoice.pdf"),
            fileName = DocumentFileName.of("Facture.pdf"),
            expectedMetadata = METADATA,
            createdAt = CREATED_AT,
            expiresAt = EXPIRES_AT,
        )

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-08-16T10:00:00Z")
        val READY_AT: Instant = Instant.parse("2026-08-16T10:01:00Z")
        val CONSUMED_AT: Instant = Instant.parse("2026-08-16T10:02:00Z")
        val EXPIRES_AT: Instant = Instant.parse("2026-08-16T10:05:00Z")
        val METADATA =
            DocumentMetadata(
                mediaType = DocumentMediaType.of("application/pdf"),
                size = DocumentSize.ofBytes(512),
                checksum = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32))),
            )
    }
}
