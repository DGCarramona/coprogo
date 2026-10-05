package tech.justdev.domain.ledger.event

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.domain.document.entity.DocumentMetadata
import tech.justdev.domain.document.valueobject.DocumentFileName
import tech.justdev.domain.document.valueobject.DocumentMediaType
import tech.justdev.domain.document.valueobject.DocumentSha256
import tech.justdev.domain.document.valueobject.DocumentSize
import tech.justdev.domain.document.valueobject.DocumentStorageKey
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.ledger.effect.MemberBalanceTransfer
import tech.justdev.domain.ledger.valueobject.LedgerEventId
import tech.justdev.domain.reimbursement.entity.Reimbursement
import tech.justdev.domain.reimbursement.entity.ReimbursementSupportingDocument
import tech.justdev.domain.reimbursement.valueobject.ReimbursementId
import tech.justdev.domain.reimbursement.valueobject.ReimbursementStatus
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.testUuid
import java.time.Instant
import java.util.Base64

class AcceptedReimbursementLedgerEventTest {
    @Test
    fun `from should reverse the payment direction to reduce the payer debt`() {
        val reimbursement = acceptedReimbursement()

        assertEquals(
            AcceptedReimbursementLedgerEvent(
                id = LedgerEventId.fromName("accepted-reimbursement:${REIMBURSEMENT_ID.toPrimitive()}"),
                group = GROUP,
                reimbursement = REIMBURSEMENT_ID,
                occurredAt = ACCEPTED_AT,
                balanceTransfer =
                    MemberBalanceTransfer(
                        fromMember = memberEmail("alice"),
                        toMember = memberEmail("bob"),
                        amount = MoneyAmount.ofCents(4_200),
                    ),
            ),
            AcceptedReimbursementLedgerEvent.from(reimbursement),
        )
    }

    @Test
    fun `from should reject a reimbursement that is not accepted`() {
        val pending =
            Reimbursement.restore(
                id = REIMBURSEMENT_ID,
                group = GROUP,
                paidBy = memberEmail("bob"),
                receivedBy = memberEmail("alice"),
                amount = MoneyAmount.ofCents(4_200),
                reimbursedAt = REIMBURSED_AT,
                declaredBy = memberEmail("bob"),
                declaredAt = DECLARED_AT,
                status = ReimbursementStatus.PendingReview,
                supportingDocuments = listOf(supportingDocument()),
            )

        val error =
            assertThrows<IllegalArgumentException> {
                AcceptedReimbursementLedgerEvent.from(pending)
            }

        assertEquals("reimbursement must be accepted before it can produce a ledger event", error.message)
    }

    private fun acceptedReimbursement(): Reimbursement =
        Reimbursement.recordDirect(
            id = REIMBURSEMENT_ID,
            group = GROUP,
            paidBy = memberEmail("bob"),
            receivedBy = memberEmail("alice"),
            amount = MoneyAmount.ofCents(4_200),
            reimbursedAt = REIMBURSED_AT,
            declaredBy = memberEmail("alice"),
            declaredAt = ACCEPTED_AT,
        )

    private fun supportingDocument(): ReimbursementSupportingDocument =
        ReimbursementSupportingDocument.restore(
            sourceUploadIntent = DocumentUploadIntentId(testUuid("accepted-reimbursement-document")),
            group = GROUP,
            uploader = memberEmail("bob"),
            storageKey = DocumentStorageKey.of("groups/accepted-reimbursement/documents/proof.pdf"),
            fileName = DocumentFileName.of("proof.pdf"),
            metadata =
                DocumentMetadata(
                    mediaType = DocumentMediaType.of("application/pdf"),
                    size = DocumentSize.ofBytes(512),
                    checksum = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32))),
                ),
            attachedAt = DECLARED_AT,
        )

    private companion object {
        val GROUP = groupId("accepted-reimbursement-ledger")
        val REIMBURSEMENT_ID = ReimbursementId(testUuid("accepted-reimbursement-ledger"))
        val REIMBURSED_AT: Instant = Instant.parse("2026-04-02T10:00:00Z")
        val DECLARED_AT: Instant = Instant.parse("2026-04-03T09:00:00Z")
        val ACCEPTED_AT: Instant = Instant.parse("2026-04-03T10:00:00Z")
    }
}
