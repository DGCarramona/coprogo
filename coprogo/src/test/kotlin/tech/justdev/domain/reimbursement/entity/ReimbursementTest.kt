package tech.justdev.domain.reimbursement.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.domain.document.entity.DocumentMetadata
import tech.justdev.domain.document.entity.DocumentUploadIntent
import tech.justdev.domain.document.valueobject.DocumentFileName
import tech.justdev.domain.document.valueobject.DocumentMediaType
import tech.justdev.domain.document.valueobject.DocumentSha256
import tech.justdev.domain.document.valueobject.DocumentSize
import tech.justdev.domain.document.valueobject.DocumentStorageKey
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.reimbursement.valueobject.ReimbursementId
import tech.justdev.domain.reimbursement.valueobject.ReimbursementStatus
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.testUuid
import java.time.Instant
import java.util.Base64

class ReimbursementTest {
    @Nested
    inner class RecordDirect {
        @Test
        fun `should immediately accept a reimbursement recorded by its receiver`() {
            val reimbursement = directReimbursement()

            assertEquals(
                ReimbursementSnapshot(
                    id = ID,
                    group = groupId("direct-reimbursement"),
                    paidBy = memberEmail("bob"),
                    receivedBy = memberEmail("alice"),
                    amount = MoneyAmount.ofCents(4_200),
                    reimbursedAt = REIMBURSED_AT,
                    declaredBy = memberEmail("alice"),
                    declaredAt = DECLARED_AT,
                    status = ReimbursementStatus.Accepted(DECLARED_AT),
                    supportingDocuments = emptyList(),
                ),
                snapshotOf(reimbursement),
            )
        }

        @Test
        fun `should reject a direct reimbursement not recorded by its receiver`() {
            val error =
                assertThrows<IllegalArgumentException> {
                    directReimbursement(declaredBy = memberEmail("bob"))
                }

            assertEquals("direct reimbursement must be recorded by its receiver", error.message)
        }

        @Test
        fun `should reject a reimbursement between the same member`() {
            val alice = memberEmail("alice")

            val error = assertThrows<IllegalArgumentException> { directReimbursement(paidBy = alice, receivedBy = alice) }

            assertEquals("reimbursement payer and receiver must be different", error.message)
        }

        @Test
        fun `should reject a zero amount`() {
            val error = assertThrows<IllegalArgumentException> { directReimbursement(amount = MoneyAmount.ZERO) }

            assertEquals("reimbursement amount must be strictly positive", error.message)
        }

        @Test
        fun `should reject a reimbursement declared before it occurred`() {
            val error =
                assertThrows<IllegalArgumentException> {
                    directReimbursement(reimbursedAt = DECLARED_AT.plusNanos(1))
                }

            assertEquals("reimbursement must not occur after its declaration", error.message)
        }
    }

    @Nested
    inner class DeclareWithSupportingDocuments {
        @Test
        fun `should declare a reimbursement from its payer with self-contained consumed document snapshots pending review`() {
            val firstIntent = consumedIntent("first")
            val secondIntent = consumedIntent("second")

            val reimbursement =
                Reimbursement.declareWithSupportingDocuments(
                    id = ID,
                    group = GROUP,
                    paidBy = memberEmail("bob"),
                    receivedBy = memberEmail("alice"),
                    amount = MoneyAmount.ofCents(4_200),
                    reimbursedAt = REIMBURSED_AT,
                    declaredBy = memberEmail("bob"),
                    declaredAt = DECLARED_AT,
                    supportingDocumentUploadIntents = listOf(firstIntent, secondIntent),
                )

            assertEquals(
                ReimbursementSnapshot(
                    id = ID,
                    group = GROUP,
                    paidBy = memberEmail("bob"),
                    receivedBy = memberEmail("alice"),
                    amount = MoneyAmount.ofCents(4_200),
                    reimbursedAt = REIMBURSED_AT,
                    declaredBy = memberEmail("bob"),
                    declaredAt = DECLARED_AT,
                    status = ReimbursementStatus.PendingReview,
                    supportingDocuments =
                        listOf(
                            ReimbursementSupportingDocument.fromConsumedUploadIntent(firstIntent),
                            ReimbursementSupportingDocument.fromConsumedUploadIntent(secondIntent),
                        ),
                ),
                snapshotOf(reimbursement),
            )
        }

        @Test
        fun `should reject a reimbursement declared by someone other than its payer`() {
            val error =
                assertThrows<IllegalArgumentException> {
                    declaredReimbursement(declaredBy = memberEmail("alice"))
                }

            assertEquals("documented reimbursement must be declared by its payer", error.message)
        }

        @Test
        fun `should reject a reimbursement without supporting documents`() {
            val error =
                assertThrows<IllegalArgumentException> {
                    declaredReimbursement(supportingDocumentUploadIntents = emptyList())
                }

            assertEquals("documented reimbursement requires at least one supporting document", error.message)
        }

        @Test
        fun `should reject a supporting document upload intent that is not consumed`() {
            val error =
                assertThrows<IllegalArgumentException> {
                    declaredReimbursement(supportingDocumentUploadIntents = listOf(readyIntent("ready")))
                }

            assertEquals("documented reimbursement supporting document requires a consumed upload intent", error.message)
        }

        @Test
        fun `should reject a supporting document from another group`() {
            val error =
                assertThrows<IllegalArgumentException> {
                    declaredReimbursement(
                        supportingDocumentUploadIntents = listOf(consumedIntent("other", group = groupId("other-group"))),
                    )
                }

            assertEquals("reimbursement and supporting document must belong to the same group", error.message)
        }

        @Test
        fun `should reject a supporting document uploaded by someone other than the payer`() {
            val error =
                assertThrows<IllegalArgumentException> {
                    declaredReimbursement(
                        supportingDocumentUploadIntents =
                            listOf(
                                consumedIntent(
                                    "alice",
                                    uploader = memberEmail("alice"),
                                ),
                            ),
                    )
                }

            assertEquals("documented reimbursement supporting document must be uploaded by its declarer", error.message)
        }

        @Test
        fun `should reject duplicate supporting document upload intents`() {
            val intent = consumedIntent("duplicate")

            val error =
                assertThrows<IllegalArgumentException> {
                    declaredReimbursement(supportingDocumentUploadIntents = listOf(intent, intent))
                }

            assertEquals("documented reimbursement supporting documents must use distinct upload intents", error.message)
        }
    }

    @Nested
    inner class Restore {
        @Test
        fun `should restore an accepted reimbursement`() {
            val acceptedAt = DECLARED_AT.plusSeconds(60)

            val reimbursement =
                Reimbursement.restore(
                    id = ID,
                    group = groupId("direct-reimbursement"),
                    paidBy = memberEmail("bob"),
                    receivedBy = memberEmail("alice"),
                    amount = MoneyAmount.ofCents(4_200),
                    reimbursedAt = REIMBURSED_AT,
                    declaredBy = memberEmail("alice"),
                    declaredAt = DECLARED_AT,
                    status = ReimbursementStatus.Accepted(acceptedAt),
                )

            assertEquals(ReimbursementStatus.Accepted(acceptedAt), reimbursement.status)
        }

        @Test
        fun `should restore a reimbursement accepted at its declaration time`() {
            val reimbursement =
                Reimbursement.restore(
                    id = ID,
                    group = groupId("direct-reimbursement"),
                    paidBy = memberEmail("bob"),
                    receivedBy = memberEmail("alice"),
                    amount = MoneyAmount.ofCents(4_200),
                    reimbursedAt = REIMBURSED_AT,
                    declaredBy = memberEmail("alice"),
                    declaredAt = DECLARED_AT,
                    status = ReimbursementStatus.Accepted(DECLARED_AT),
                )

            assertEquals(ReimbursementStatus.Accepted(DECLARED_AT), reimbursement.status)
        }

        @Test
        fun `should reject an accepted direct reimbursement declared by its payer`() {
            val error =
                assertThrows<IllegalArgumentException> {
                    Reimbursement.restore(
                        id = ID,
                        group = GROUP,
                        paidBy = memberEmail("bob"),
                        receivedBy = memberEmail("alice"),
                        amount = MoneyAmount.ofCents(4_200),
                        reimbursedAt = REIMBURSED_AT,
                        declaredBy = memberEmail("bob"),
                        declaredAt = DECLARED_AT,
                        status = ReimbursementStatus.Accepted(DECLARED_AT),
                    )
                }

            assertEquals("direct reimbursement must be recorded by its receiver", error.message)
        }

        @Test
        fun `should restore a reimbursement declared at its reimbursement time`() {
            val reimbursement =
                Reimbursement.restore(
                    id = ID,
                    group = groupId("direct-reimbursement"),
                    paidBy = memberEmail("bob"),
                    receivedBy = memberEmail("alice"),
                    amount = MoneyAmount.ofCents(4_200),
                    reimbursedAt = DECLARED_AT,
                    declaredBy = memberEmail("alice"),
                    declaredAt = DECLARED_AT,
                    status = ReimbursementStatus.Accepted(DECLARED_AT),
                )

            assertEquals(DECLARED_AT, reimbursement.reimbursedAt)
        }

        @Test
        fun `should reject acceptance before declaration`() {
            val error =
                assertThrows<IllegalArgumentException> {
                    Reimbursement.restore(
                        id = ID,
                        group = groupId("direct-reimbursement"),
                        paidBy = memberEmail("bob"),
                        receivedBy = memberEmail("alice"),
                        amount = MoneyAmount.ofCents(4_200),
                        reimbursedAt = REIMBURSED_AT,
                        declaredBy = memberEmail("alice"),
                        declaredAt = DECLARED_AT,
                        status = ReimbursementStatus.Accepted(DECLARED_AT.minusNanos(1)),
                    )
                }

            assertEquals("reimbursement acceptance must not precede its declaration", error.message)
        }

        @Test
        fun `should reject a declaration by a member outside the reimbursement`() {
            val error =
                assertThrows<IllegalArgumentException> {
                    Reimbursement.restore(
                        id = ID,
                        group = groupId("direct-reimbursement"),
                        paidBy = memberEmail("bob"),
                        receivedBy = memberEmail("alice"),
                        amount = MoneyAmount.ofCents(4_200),
                        reimbursedAt = REIMBURSED_AT,
                        declaredBy = memberEmail("carol"),
                        declaredAt = DECLARED_AT,
                        status = ReimbursementStatus.Accepted(DECLARED_AT),
                    )
                }

            assertEquals("reimbursement must be declared by its payer or receiver", error.message)
        }

        @Test
        fun `should restore a reimbursement pending review with its supporting document snapshots`() {
            val reimbursement =
                Reimbursement.restore(
                    id = ID,
                    group = GROUP,
                    paidBy = memberEmail("bob"),
                    receivedBy = memberEmail("alice"),
                    amount = MoneyAmount.ofCents(4_200),
                    reimbursedAt = REIMBURSED_AT,
                    declaredBy = memberEmail("bob"),
                    declaredAt = DECLARED_AT,
                    status = ReimbursementStatus.PendingReview,
                    supportingDocuments = listOf(ReimbursementSupportingDocument.fromConsumedUploadIntent(consumedIntent("restore"))),
                )

            assertEquals(ReimbursementStatus.PendingReview, reimbursement.status)
        }

        @Test
        fun `should restore an accepted documented reimbursement declared by its payer`() {
            val document = ReimbursementSupportingDocument.fromConsumedUploadIntent(consumedIntent("accepted-document"))

            val reimbursement =
                Reimbursement.restore(
                    id = ID,
                    group = GROUP,
                    paidBy = memberEmail("bob"),
                    receivedBy = memberEmail("alice"),
                    amount = MoneyAmount.ofCents(4_200),
                    reimbursedAt = REIMBURSED_AT,
                    declaredBy = memberEmail("bob"),
                    declaredAt = DECLARED_AT,
                    status = ReimbursementStatus.Accepted(DECLARED_AT.plusSeconds(60)),
                    supportingDocuments = listOf(document),
                )

            assertEquals(listOf(document), reimbursement.supportingDocuments)
        }

        @Test
        fun `should retain a defensive copy of restored supporting document snapshots`() {
            val documents =
                mutableListOf(
                    ReimbursementSupportingDocument.fromConsumedUploadIntent(consumedIntent("defensive-copy")),
                )

            val reimbursement =
                Reimbursement.restore(
                    id = ID,
                    group = GROUP,
                    paidBy = memberEmail("bob"),
                    receivedBy = memberEmail("alice"),
                    amount = MoneyAmount.ofCents(4_200),
                    reimbursedAt = REIMBURSED_AT,
                    declaredBy = memberEmail("bob"),
                    declaredAt = DECLARED_AT,
                    status = ReimbursementStatus.PendingReview,
                    supportingDocuments = documents,
                )

            documents.clear()

            assertEquals(
                listOf(ReimbursementSupportingDocument.fromConsumedUploadIntent(consumedIntent("defensive-copy"))),
                reimbursement.supportingDocuments,
            )
        }

        @Test
        fun `should reject a pending review reimbursement restored without supporting documents`() {
            val error =
                assertThrows<IllegalArgumentException> {
                    Reimbursement.restore(
                        id = ID,
                        group = GROUP,
                        paidBy = memberEmail("bob"),
                        receivedBy = memberEmail("alice"),
                        amount = MoneyAmount.ofCents(4_200),
                        reimbursedAt = REIMBURSED_AT,
                        declaredBy = memberEmail("bob"),
                        declaredAt = DECLARED_AT,
                        status = ReimbursementStatus.PendingReview,
                        supportingDocuments = emptyList(),
                    )
                }

            assertEquals("documented reimbursement requires at least one supporting document", error.message)
        }

        @Test
        fun `should reject restored supporting document snapshots from another group`() {
            val error =
                assertThrows<IllegalArgumentException> {
                    restoreDocumentedReimbursement(
                        supportingDocuments =
                            listOf(
                                ReimbursementSupportingDocument.fromConsumedUploadIntent(
                                    consumedIntent("other-group", group = groupId("other-group")),
                                ),
                            ),
                    )
                }

            assertEquals("reimbursement and supporting document must belong to the same group", error.message)
        }

        @Test
        fun `should reject restored supporting document snapshots uploaded by another member`() {
            val error =
                assertThrows<IllegalArgumentException> {
                    restoreDocumentedReimbursement(
                        supportingDocuments =
                            listOf(
                                ReimbursementSupportingDocument.fromConsumedUploadIntent(
                                    consumedIntent("other-uploader", uploader = memberEmail("alice")),
                                ),
                            ),
                    )
                }

            assertEquals("documented reimbursement supporting document must be uploaded by its declarer", error.message)
        }

        @Test
        fun `should reject restored duplicate supporting document snapshots`() {
            val document = ReimbursementSupportingDocument.fromConsumedUploadIntent(consumedIntent("duplicate-restored"))

            val error =
                assertThrows<IllegalArgumentException> {
                    restoreDocumentedReimbursement(supportingDocuments = listOf(document, document))
                }

            assertEquals("documented reimbursement supporting documents must use distinct upload intents", error.message)
        }
    }

    private fun directReimbursement(
        paidBy: MemberEmail = memberEmail("bob"),
        receivedBy: MemberEmail = memberEmail("alice"),
        amount: MoneyAmount = MoneyAmount.ofCents(4_200),
        reimbursedAt: Instant = REIMBURSED_AT,
        declaredBy: MemberEmail = receivedBy,
    ): Reimbursement =
        Reimbursement.recordDirect(
            id = ID,
            group = groupId("direct-reimbursement"),
            paidBy = paidBy,
            receivedBy = receivedBy,
            amount = amount,
            reimbursedAt = reimbursedAt,
            declaredBy = declaredBy,
            declaredAt = DECLARED_AT,
        )

    private fun declaredReimbursement(
        declaredBy: MemberEmail = memberEmail("bob"),
        supportingDocumentUploadIntents: List<DocumentUploadIntent> = listOf(consumedIntent("default")),
    ): Reimbursement =
        Reimbursement.declareWithSupportingDocuments(
            id = ID,
            group = GROUP,
            paidBy = memberEmail("bob"),
            receivedBy = memberEmail("alice"),
            amount = MoneyAmount.ofCents(4_200),
            reimbursedAt = REIMBURSED_AT,
            declaredBy = declaredBy,
            declaredAt = DECLARED_AT,
            supportingDocumentUploadIntents = supportingDocumentUploadIntents,
        )

    private fun restoreDocumentedReimbursement(supportingDocuments: List<ReimbursementSupportingDocument>): Reimbursement =
        Reimbursement.restore(
            id = ID,
            group = GROUP,
            paidBy = memberEmail("bob"),
            receivedBy = memberEmail("alice"),
            amount = MoneyAmount.ofCents(4_200),
            reimbursedAt = REIMBURSED_AT,
            declaredBy = memberEmail("bob"),
            declaredAt = DECLARED_AT,
            status = ReimbursementStatus.PendingReview,
            supportingDocuments = supportingDocuments,
        )

    private fun readyIntent(
        seed: String,
        group: GroupId = GROUP,
        uploader: MemberEmail = memberEmail("bob"),
    ): DocumentUploadIntent =
        DocumentUploadIntent
            .create(
                id = DocumentUploadIntentId(testUuid("doc-$seed")),
                group = group,
                uploader = uploader,
                storageKey = DocumentStorageKey.of("groups/${group.toPrimitive()}/documents/$seed.pdf"),
                fileName = DocumentFileName.of("$seed.pdf"),
                expectedMetadata = DOCUMENT_METADATA,
                createdAt = DOCUMENT_CREATED_AT,
                expiresAt = DOCUMENT_EXPIRES_AT,
            ).markReady(DOCUMENT_METADATA, DOCUMENT_READY_AT)

    private fun consumedIntent(
        seed: String,
        group: GroupId = GROUP,
        uploader: MemberEmail = memberEmail("bob"),
    ): DocumentUploadIntent = readyIntent(seed, group, uploader).consume(DOCUMENT_CONSUMED_AT)

    private fun snapshotOf(reimbursement: Reimbursement): ReimbursementSnapshot =
        ReimbursementSnapshot(
            id = reimbursement.id,
            group = reimbursement.group,
            paidBy = reimbursement.paidBy,
            receivedBy = reimbursement.receivedBy,
            amount = reimbursement.amount,
            reimbursedAt = reimbursement.reimbursedAt,
            declaredBy = reimbursement.declaredBy,
            declaredAt = reimbursement.declaredAt,
            status = reimbursement.status,
            supportingDocuments = reimbursement.supportingDocuments,
        )

    private data class ReimbursementSnapshot(
        val id: ReimbursementId,
        val group: GroupId,
        val paidBy: MemberEmail,
        val receivedBy: MemberEmail,
        val amount: MoneyAmount,
        val reimbursedAt: Instant,
        val declaredBy: MemberEmail,
        val declaredAt: Instant,
        val status: ReimbursementStatus,
        val supportingDocuments: List<ReimbursementSupportingDocument>,
    )

    private companion object {
        val ID = ReimbursementId(testUuid("direct-reimbursement"))
        val GROUP = groupId("direct-reimbursement")
        val REIMBURSED_AT: Instant = Instant.parse("2026-09-27T18:00:00Z")
        val DECLARED_AT: Instant = Instant.parse("2026-09-28T09:00:00Z")
        val DOCUMENT_CREATED_AT: Instant = Instant.parse("2026-09-27T17:00:00Z")
        val DOCUMENT_READY_AT: Instant = Instant.parse("2026-09-27T17:30:00Z")
        val DOCUMENT_CONSUMED_AT: Instant = Instant.parse("2026-09-27T17:45:00Z")
        val DOCUMENT_EXPIRES_AT: Instant = Instant.parse("2026-09-27T18:30:00Z")
        val DOCUMENT_METADATA =
            DocumentMetadata(
                mediaType = DocumentMediaType.of("application/pdf"),
                size = DocumentSize.ofBytes(128),
                checksum = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32))),
            )
    }
}
