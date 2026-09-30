package tech.justdev.domain.reimbursement.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.reimbursement.valueobject.ReimbursementId
import tech.justdev.domain.reimbursement.valueobject.ReimbursementStatus
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.testUuid
import java.time.Instant

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
    )

    private companion object {
        val ID = ReimbursementId(testUuid("direct-reimbursement"))
        val REIMBURSED_AT: Instant = Instant.parse("2026-09-27T18:00:00Z")
        val DECLARED_AT: Instant = Instant.parse("2026-09-28T09:00:00Z")
    }
}
