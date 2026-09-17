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
            assertEquals(null, attachment.deletion)
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

        @Test
        fun `should restore a deleted attachment from its immutable audit data`() {
            val deletion =
                SupportingDocumentAttachmentDeletion(
                    deletedBy = memberEmail("document-remover"),
                    deletedAt = DELETED_AT,
                )

            val attachment =
                ExpenseSupportingDocumentAttachment.restore(
                    sourceUploadIntent = DocumentUploadIntentId(testUuid("deleted-support-document")),
                    group = groupId("documents"),
                    expense = expenseId("documented"),
                    deletion = deletion,
                )

            assertEquals(deletion, attachment.deletion)
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
            assertEquals(null, attachment.deletion)
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
    inner class ReplaceWith {
        @Test
        fun `should create a successor for a consumed upload intent in the same expense`() {
            val previousIntent = pendingIntent("previous").markReady(METADATA, READY_AT).consume(CONSUMED_AT)
            val replacementIntent = pendingIntent("replacement").markReady(METADATA, READY_AT).consume(CONSUMED_AT)
            val previous = ExpenseSupportingDocumentAttachment.attach(expenseId("documented"), previousIntent.group, previousIntent)

            val replacement = previous.replaceWith(replacementIntent)

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
                    previous.replaceWith(replacementIntent)
                }

            assertEquals("replaced and replacement documents must belong to the same group", error.message)
        }

        @Test
        fun `should reject reusing the replaced upload intent as its successor`() {
            val intent = pendingIntent("same-intent").markReady(METADATA, READY_AT).consume(CONSUMED_AT)
            val previous = ExpenseSupportingDocumentAttachment.attach(expenseId("documented"), intent.group, intent)

            val error =
                assertThrows<IllegalArgumentException> {
                    previous.replaceWith(intent)
                }

            assertEquals("replaced and replacement documents must use distinct upload intents", error.message)
        }

        @Test
        fun `should reject replacing a deleted document`() {
            val previousIntent = pendingIntent("deleted-previous").markReady(METADATA, READY_AT).consume(CONSUMED_AT)
            val replacementIntent = pendingIntent("deleted-replacement").markReady(METADATA, READY_AT).consume(CONSUMED_AT)
            val deleted =
                ExpenseSupportingDocumentAttachment
                    .attach(expenseId("documented"), previousIntent.group, previousIntent)
                    .delete(memberEmail("document-remover"), DELETED_AT)

            val error =
                assertThrows<IllegalArgumentException> {
                    deleted.replaceWith(replacementIntent)
                }

            assertEquals("deleted supporting document cannot be replaced", error.message)
        }
    }

    @Nested
    inner class Delete {
        @Test
        fun `should create an audited immutable tombstone`() {
            val attachment =
                ExpenseSupportingDocumentAttachment
                    .attach(
                        expense = expenseId("documented"),
                        group = groupId("documents"),
                        intent = pendingIntent().markReady(METADATA, READY_AT).consume(CONSUMED_AT),
                    ).delete(memberEmail("document-remover"), DELETED_AT)

            assertEquals(
                SupportingDocumentAttachmentDeletion(
                    deletedBy = memberEmail("document-remover"),
                    deletedAt = DELETED_AT,
                ),
                attachment.deletion,
            )
        }

        @Test
        fun `should reject deleting an attachment twice`() {
            val deleted =
                ExpenseSupportingDocumentAttachment
                    .attach(
                        expense = expenseId("documented"),
                        group = groupId("documents"),
                        intent = pendingIntent().markReady(METADATA, READY_AT).consume(CONSUMED_AT),
                    ).delete(memberEmail("document-remover"), DELETED_AT)

            val error =
                assertThrows<IllegalStateException> {
                    deleted.delete(memberEmail("another-remover"), DELETED_AT.plusSeconds(1))
                }

            assertEquals("supporting document attachment has already been deleted", error.message)
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
        val DELETED_AT: Instant = Instant.parse("2026-08-16T10:03:00Z")
        val EXPIRES_AT: Instant = Instant.parse("2026-08-16T10:05:00Z")
        val METADATA =
            DocumentMetadata(
                mediaType = DocumentMediaType.of("application/pdf"),
                size = DocumentSize.ofBytes(512),
                checksum = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32))),
            )
    }
}
