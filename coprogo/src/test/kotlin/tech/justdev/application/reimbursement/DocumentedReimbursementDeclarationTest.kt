package tech.justdev.application.reimbursement

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
import tech.justdev.domain.reimbursement.entity.Reimbursement
import tech.justdev.domain.reimbursement.valueobject.ReimbursementId
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.testUuid
import java.time.Instant
import java.util.Base64

class DocumentedReimbursementDeclarationTest {
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class From {
        private fun invalidDeclarationCases(): List<InvalidDeclarationCase> {
            val first = readyIntent("first")
            val consumedFirst = first.consume(DECLARED_AT)
            val consumedSecond = readyIntent("second").consume(DECLARED_AT)
            val reimbursement = reimbursement(listOf(consumedFirst))
            return listOf(
                InvalidDeclarationCase(
                    reimbursement = reimbursement,
                    uploadIntents = listOf(first),
                    expectedMessage = "documented reimbursement declaration requires consumed upload intents",
                ),
                InvalidDeclarationCase(
                    reimbursement = reimbursement,
                    uploadIntents = listOf(consumedSecond),
                    expectedMessage = "reimbursement supporting documents must exactly match consumed upload intents",
                ),
                InvalidDeclarationCase(
                    reimbursement = reimbursement,
                    uploadIntents = listOf(consumedFirst, consumedFirst),
                    expectedMessage = "reimbursement supporting documents must exactly match consumed upload intents",
                ),
                InvalidDeclarationCase(
                    reimbursement = reimbursement.accept(memberEmail("alice"), DECLARED_AT.plusSeconds(1)),
                    uploadIntents = listOf(consumedFirst),
                    expectedMessage = "documented reimbursement declaration must be pending review",
                ),
            )
        }

        @Test
        fun `should encapsulate a reimbursement and matching consumed upload intents defensively`() {
            val consumed = readyIntent("matching").consume(DECLARED_AT)
            val mutableIntents = mutableListOf(consumed)
            val reimbursement = reimbursement(mutableIntents)

            val declaration = DocumentedReimbursementDeclaration.from(reimbursement, mutableIntents)
            mutableIntents.clear()

            assertEquals(reimbursement, declaration.reimbursement)
            assertEquals(listOf(consumed), declaration.consumedUploadIntents)
        }

        @ParameterizedTest(name = "{index}")
        @MethodSource("invalidDeclarationCases")
        fun `should reject inconsistent declarations`(case: InvalidDeclarationCase) {
            val error =
                assertThrows<IllegalArgumentException> {
                    DocumentedReimbursementDeclaration.from(case.reimbursement, case.uploadIntents)
                }

            assertEquals(case.expectedMessage, error.message)
        }
    }

    private fun reimbursement(consumedUploadIntents: List<DocumentUploadIntent>): Reimbursement =
        Reimbursement.declareWithSupportingDocuments(
            id = ReimbursementId(testUuid("reimbursement:declaration")),
            group = groupId("declaration"),
            paidBy = memberEmail("bob"),
            receivedBy = memberEmail("alice"),
            amount = MoneyAmount.ofCents(4_200),
            reimbursedAt = REIMBURSED_AT,
            declaredBy = memberEmail("bob"),
            declaredAt = DECLARED_AT,
            supportingDocumentUploadIntents = consumedUploadIntents,
        )

    private fun readyIntent(seed: String): DocumentUploadIntent =
        DocumentUploadIntent
            .create(
                id = DocumentUploadIntentId(testUuid("document:$seed")),
                group = groupId("declaration"),
                uploader = memberEmail("bob"),
                storageKey = DocumentStorageKey.of("groups/declaration/documents/$seed.pdf"),
                fileName = DocumentFileName.of("$seed.pdf"),
                expectedMetadata = METADATA,
                createdAt = CREATED_AT,
                expiresAt = EXPIRES_AT,
            ).markReady(METADATA, READY_AT)

    data class InvalidDeclarationCase(
        val reimbursement: Reimbursement,
        val uploadIntents: List<DocumentUploadIntent>,
        val expectedMessage: String,
    )

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-04-03T08:00:00Z")
        val READY_AT: Instant = Instant.parse("2026-04-03T09:00:00Z")
        val REIMBURSED_AT: Instant = Instant.parse("2026-04-03T09:30:00Z")
        val DECLARED_AT: Instant = Instant.parse("2026-04-03T10:00:00Z")
        val EXPIRES_AT: Instant = Instant.parse("2026-04-03T11:00:00Z")
        val METADATA =
            DocumentMetadata(
                mediaType = DocumentMediaType.of("application/pdf"),
                size = DocumentSize.ofBytes(512),
                checksum = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32))),
            )
    }
}
