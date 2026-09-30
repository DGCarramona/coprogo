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
import tech.justdev.domain.document.valueobject.SupportingDocumentAttachmentDeletion
import tech.justdev.domain.expense.exception.ExpenseSupportingDocumentUnavailableException
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.testUuid
import java.time.Instant
import java.util.Base64
import java.util.stream.Stream

class ExpenseSupportingDocumentsTest {
    @Nested
    inner class Restore {
        @Test
        fun `should compare collections by their complete history`() {
            val documents = listOf(document("original"))

            assertEquals(
                ExpenseSupportingDocuments.restore(documents),
                ExpenseSupportingDocuments.restore(documents),
            )
        }

        @Test
        fun `should expose all documents and only active leaf documents as current`() {
            val original = document("original")
            val replacement = document("replacement", replaces = original.sourceUploadIntent)
            val deleted = document("deleted", deletion = SupportingDocumentAttachmentDeletion(DELETER, DELETED_AT))

            val documents = ExpenseSupportingDocuments.restore(listOf(original, replacement, deleted))

            assertEquals(listOf(original, replacement, deleted), documents.all)
            assertEquals(listOf(replacement), documents.current)
            assertEquals(false, documents.isEmpty)
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("tech.justdev.domain.document.entity.ExpenseSupportingDocumentsTest#invalidDocumentHistories")
        fun `should reject an invalid supporting document history`(
            caseName: String,
            documents: List<ExpenseSupportingDocument>,
            expectedMessage: String,
        ) {
            val error = assertThrows<IllegalArgumentException> { ExpenseSupportingDocuments.restore(documents) }

            assertEquals(expectedMessage, error.message)
        }
    }

    @Nested
    inner class From {
        @Test
        fun `should create a collection from supporting document snapshots`() {
            val documents =
                ExpenseSupportingDocuments.from(
                    listOf(
                        document("first"),
                        document("second"),
                    ),
                )

            assertEquals(
                listOf(documentId("first"), documentId("second")),
                documents.all.map(ExpenseSupportingDocument::sourceUploadIntent),
            )
            assertEquals(
                listOf(documentId("first"), documentId("second")),
                documents.current.map(ExpenseSupportingDocument::sourceUploadIntent),
            )
        }
    }

    @Nested
    inner class Replace {
        @Test
        fun `should replace the selected current document while retaining history`() {
            val documents =
                ExpenseSupportingDocuments.from(
                    listOf(document("first"), document("second")),
                )

            val replacement = documents.replace(documentId("first"), consumedIntent("replacement"))

            assertEquals(
                listOf(documentId("first"), documentId("second"), documentId("replacement")),
                replacement.supportingDocuments.all.map(ExpenseSupportingDocument::sourceUploadIntent),
            )
            assertEquals(
                listOf(documentId("second"), documentId("replacement")),
                replacement.supportingDocuments.current.map(ExpenseSupportingDocument::sourceUploadIntent),
            )
            assertEquals(documentId("first"), replacement.replacement.replacesSourceUploadIntent)
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("tech.justdev.domain.document.entity.ExpenseSupportingDocumentsTest#unavailableReplacementTargets")
        fun `should reject an absent or historical document`(
            caseName: String,
            documents: ExpenseSupportingDocuments,
            targetSeed: String,
        ) {
            assertThrows<ExpenseSupportingDocumentUnavailableException> {
                documents.replace(documentId(targetSeed), consumedIntent("another-replacement"))
            }
        }
    }

    @Nested
    inner class Delete {
        @ParameterizedTest(name = "{0}")
        @MethodSource("tech.justdev.domain.document.entity.ExpenseSupportingDocumentsTest#unavailableDeletionTargets")
        fun `should reject an absent or historical document`(
            caseName: String,
            documents: ExpenseSupportingDocuments,
            targetSeed: String,
        ) {
            val error =
                assertThrows<ExpenseSupportingDocumentUnavailableException> {
                    documents.delete(documentId(targetSeed), DELETER, DELETED_AT)
                }

            assertEquals("expense supporting document is unavailable", error.message)
        }

        @Test
        fun `should delete only the selected current document while retaining history`() {
            val documents =
                ExpenseSupportingDocuments.from(
                    listOf(document("first"), document("second")),
                )

            val deletion = documents.delete(documentId("first"), DELETER, DELETED_AT)

            assertEquals(
                listOf(documentId("first"), documentId("second")),
                deletion.supportingDocuments.all.map(ExpenseSupportingDocument::sourceUploadIntent),
            )
            assertEquals(
                listOf(documentId("second")),
                deletion.supportingDocuments.current.map(ExpenseSupportingDocument::sourceUploadIntent),
            )
            assertEquals(SupportingDocumentAttachmentDeletion(DELETER, DELETED_AT), deletion.deleted.deletion)
        }
    }

    private companion object {
        val GROUP = groupId("documents")
        val DELETER = memberEmail("deleter")
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

        private fun document(
            seed: String,
            attachedAt: Instant = CONSUMED_AT,
            replaces: DocumentUploadIntentId? = null,
            deletion: SupportingDocumentAttachmentDeletion? = null,
        ): ExpenseSupportingDocument =
            ExpenseSupportingDocument.restore(
                sourceUploadIntent = documentId(seed),
                uploader = DELETER,
                storageKey = DocumentStorageKey.of("groups/documents/$seed.pdf"),
                fileName = DocumentFileName.of("$seed.pdf"),
                metadata = METADATA,
                attachedAt = attachedAt,
                replacesSourceUploadIntent = replaces,
                deletion = deletion,
            )

        private fun consumedIntent(seed: String): DocumentUploadIntent =
            DocumentUploadIntent
                .create(
                    id = documentId(seed),
                    group = GROUP,
                    uploader = DELETER,
                    storageKey = DocumentStorageKey.of("groups/documents/$seed.pdf"),
                    fileName = DocumentFileName.of("$seed.pdf"),
                    expectedMetadata = METADATA,
                    createdAt = CREATED_AT,
                    expiresAt = CREATED_AT.plusSeconds(600),
                ).markReady(METADATA, READY_AT)
                .consume(CONSUMED_AT)

        private fun documentId(seed: String): DocumentUploadIntentId = DocumentUploadIntentId(testUuid("document-$seed"))

        @JvmStatic
        fun invalidDocumentHistories(): Stream<Arguments> {
            val valid = document("valid")
            val deleted = document("deleted", deletion = SupportingDocumentAttachmentDeletion(DELETER, DELETED_AT))

            return Stream.of(
                Arguments.of(
                    "duplicate upload intent",
                    listOf(valid, valid),
                    "supporting documents must use unique upload intents",
                ),
                Arguments.of(
                    "missing replacement source",
                    listOf(document("orphan", replaces = documentId("missing"))),
                    "supporting document replacements must reference an existing document",
                ),
                Arguments.of(
                    "second direct successor",
                    listOf(
                        valid,
                        document("first-successor", replaces = valid.sourceUploadIntent),
                        document("second-successor", replaces = valid.sourceUploadIntent),
                    ),
                    "supporting documents can only be replaced once",
                ),
                Arguments.of(
                    "replacement cycle",
                    listOf(
                        document("cycle-first", replaces = documentId("cycle-second")),
                        document("cycle-second", replaces = documentId("cycle-first")),
                    ),
                    "supporting document replacement history must not contain cycles",
                ),
                Arguments.of(
                    "replacement of deleted source",
                    listOf(deleted, document("successor-deleted", replaces = deleted.sourceUploadIntent)),
                    "a deleted supporting document cannot have a replacement",
                ),
                Arguments.of(
                    "replacement before source attachment",
                    listOf(
                        document("source", attachedAt = CONSUMED_AT),
                        document(
                            "replacement-before-source",
                            attachedAt = CONSUMED_AT.minusSeconds(1),
                            replaces = documentId("source"),
                        ),
                    ),
                    "supporting document replacement must not precede the source attachment",
                ),
            )
        }

        @JvmStatic
        fun unavailableReplacementTargets(): Stream<Arguments> =
            unavailableTargets().map { target -> Arguments.of(target.caseName, target.documents, target.seed) }

        @JvmStatic
        fun unavailableDeletionTargets(): Stream<Arguments> =
            unavailableTargets().map { target -> Arguments.of(target.caseName, target.documents, target.seed) }

        private fun unavailableTargets(): Stream<UnavailableTarget> {
            val historicalDocuments =
                ExpenseSupportingDocuments
                    .from(listOf(document("first")))
                    .replace(documentId("first"), consumedIntent("replacement"))
                    .supportingDocuments

            return Stream.of(
                UnavailableTarget("absent", ExpenseSupportingDocuments.empty(), "absent"),
                UnavailableTarget("historical", historicalDocuments, "first"),
            )
        }
    }

    private data class UnavailableTarget(
        val caseName: String,
        val documents: ExpenseSupportingDocuments,
        val seed: String,
    )
}
