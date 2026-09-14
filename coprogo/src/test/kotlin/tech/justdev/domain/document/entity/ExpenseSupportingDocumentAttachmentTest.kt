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
    inner class Restore {
        @Test
        fun `should restore an association from its immutable resource identities`() {
            val sourceUploadIntent = DocumentUploadIntentId(testUuid("restored-support-document"))
            val group = groupId("documents")
            val expense = expenseId("documented")

            val attachment =
                ExpenseSupportingDocumentAttachment.restore(
                    sourceUploadIntent = sourceUploadIntent,
                    group = group,
                    expense = expense,
                    replacesSourceUploadIntent = DocumentUploadIntentId(testUuid("previous-support-document")),
                )

            assertEquals(sourceUploadIntent, attachment.sourceUploadIntent)
            assertEquals(group, attachment.group)
            assertEquals(expense, attachment.expense)
            assertEquals(DocumentUploadIntentId(testUuid("previous-support-document")), attachment.replacesSourceUploadIntent)
        }

        @Test
        fun `should reject an attachment replacing itself`() {
            val sourceUploadIntent = DocumentUploadIntentId(testUuid("self-replacing-support-document"))

            val error =
                assertThrows<IllegalArgumentException> {
                    ExpenseSupportingDocumentAttachment.restore(
                        sourceUploadIntent = sourceUploadIntent,
                        group = groupId("documents"),
                        expense = expenseId("documented"),
                        replacesSourceUploadIntent = sourceUploadIntent,
                    )
                }

            assertEquals("replaced and replacement documents must use distinct upload intents", error.message)
        }
    }

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

            val errors =
                listOf(
                    attachFailure(pending),
                    attachFailure(ready),
                )

            assertEquals(
                listOf(
                    "expense supporting document requires a consumed upload intent",
                    "expense supporting document requires a consumed upload intent",
                ),
                errors,
            )
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

    @Nested
    inner class Replace {
        @Test
        fun `should create a successor for a consumed upload intent in the same expense`() {
            val previousIntent = pendingIntent("previous").markReady(METADATA, READY_AT).consume(CONSUMED_AT)
            val replacementIntent = pendingIntent("replacement").markReady(METADATA, READY_AT).consume(CONSUMED_AT)
            val previous = ExpenseSupportingDocumentAttachment.attach(expenseId("documented"), previousIntent.group, previousIntent)

            val replacement = ExpenseSupportingDocumentAttachment.replace(previous, replacementIntent)

            assertEquals(replacementIntent.id, replacement.sourceUploadIntent)
            assertEquals(previous.group, replacement.group)
            assertEquals(previous.expense, replacement.expense)
            assertEquals(previous.sourceUploadIntent, replacement.replacesSourceUploadIntent)
        }

        @Test
        fun `should reject a replacement upload intent from another group`() {
            val previousIntent = pendingIntent("previous-group").markReady(METADATA, READY_AT).consume(CONSUMED_AT)
            val replacementIntent =
                pendingIntent("replacement-other-group", groupId("other")).markReady(METADATA, READY_AT).consume(CONSUMED_AT)
            val previous = ExpenseSupportingDocumentAttachment.attach(expenseId("documented"), previousIntent.group, previousIntent)

            val error =
                assertThrows<IllegalArgumentException> {
                    ExpenseSupportingDocumentAttachment.replace(previous, replacementIntent)
                }

            assertEquals("replaced and replacement documents must belong to the same group", error.message)
        }

        @Test
        fun `should reject reusing the replaced upload intent as its successor`() {
            val intent = pendingIntent("same-intent").markReady(METADATA, READY_AT).consume(CONSUMED_AT)
            val previous = ExpenseSupportingDocumentAttachment.attach(expenseId("documented"), intent.group, intent)

            val error =
                assertThrows<IllegalArgumentException> {
                    ExpenseSupportingDocumentAttachment.replace(previous, intent)
                }

            assertEquals("replaced and replacement documents must use distinct upload intents", error.message)
        }
    }

    private fun attachFailure(intent: DocumentUploadIntent): String? =
        assertThrows<IllegalArgumentException> {
            ExpenseSupportingDocumentAttachment.attach(
                expense = expenseId("documented"),
                group = intent.group,
                intent = intent,
            )
        }.message

    private fun pendingIntent(
        seed: String = "support-document",
        group: tech.justdev.domain.shared.valueobject.GroupId = groupId("documents"),
    ): DocumentUploadIntent =
        DocumentUploadIntent.create(
            id = DocumentUploadIntentId(testUuid(seed)),
            group = group,
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
