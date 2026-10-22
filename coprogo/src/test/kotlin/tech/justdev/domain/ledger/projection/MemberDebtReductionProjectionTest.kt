package tech.justdev.domain.ledger.projection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.ledger.effect.MemberBalanceTransfer
import tech.justdev.domain.ledger.event.AcceptedExpenseLedgerEvent
import tech.justdev.domain.ledger.event.AcceptedReimbursementLedgerEvent
import tech.justdev.domain.ledger.event.CashPoolWithdrawalLedgerEvent
import tech.justdev.domain.ledger.valueobject.LedgerEventId
import tech.justdev.domain.reimbursement.valueobject.ReimbursementId
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.testsupport.acceptedExpenseLedgerEventId
import tech.justdev.testsupport.expenseId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.ledgerEventId
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.testUuid
import java.time.Instant
import java.util.UUID

class MemberDebtReductionProjectionTest {
    @Test
    fun `projectMemberDebtReductions should explain actual debt reductions from every supported event`() {
        val alice = memberEmail("alice")
        val bob = memberEmail("bob")
        val charlie = memberEmail("charlie")
        val initialDebt = expense("initial-debt", bob, Instant.parse("2026-04-03T09:00:00Z"), alice to 100)
        val reimbursement =
            AcceptedReimbursementLedgerEvent(
                id = ledgerEventId("reimbursement"),
                group = groupId("group"),
                reimbursement = ReimbursementId(testUuid("reimbursement")),
                occurredAt = Instant.parse("2026-04-03T10:00:00Z"),
                balanceTransfer = MemberBalanceTransfer(bob, alice, MoneyAmount.ofCents(30)),
            )
        val compensatingExpense = expense("compensating-expense", alice, Instant.parse("2026-04-03T11:00:00Z"), charlie to 50)
        val withdrawal =
            CashPoolWithdrawalLedgerEvent(
                id = ledgerEventId("withdrawal"),
                group = groupId("group"),
                withdrawnBy = bob,
                withdrawnAmount = MoneyAmount.ofCents(40),
                ownRevenueShareConsumed = MoneyAmount.ZERO,
                balanceTransfers = setOf(MemberBalanceTransfer(bob, alice, MoneyAmount.ofCents(40))),
                occurredAt = Instant.parse("2026-04-03T12:00:00Z"),
            )

        val reductions =
            listOf(withdrawal, compensatingExpense, initialDebt, reimbursement)
                .projectMemberDebtReductions(alice)

        assertEquals(
            listOf(
                reimbursement to 30L,
                compensatingExpense to 50L,
                withdrawal to 20L,
            ),
            reductions.map { reduction -> reduction.event to reduction.amount.inCents() },
        )
    }

    @Test
    fun `projectMemberDebtReductions should omit transfers that do not reduce an existing debt`() {
        val alice = memberEmail("alice")
        val expense = expense("credit", alice, Instant.parse("2026-04-03T10:00:00Z"), memberEmail("bob") to 100)

        assertEquals(emptyList<MemberDebtReduction>(), listOf(expense).projectMemberDebtReductions(alice))
    }

    @Test
    fun `projectMemberDebtReductions should break equal timestamps by event id`() {
        val alice = memberEmail("alice")
        val bob = memberEmail("bob")
        val occurredAt = Instant.parse("2026-04-03T10:00:00Z")
        val debt =
            expense(
                seed = "same-time-debt",
                paidBy = bob,
                occurredAt = occurredAt,
                transfer = alice to 100,
                id = LedgerEventId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
            )
        val reduction =
            AcceptedReimbursementLedgerEvent(
                id = LedgerEventId(UUID.fromString("00000000-0000-0000-0000-000000000002")),
                group = groupId("group"),
                reimbursement = ReimbursementId(testUuid("same-time-reimbursement")),
                occurredAt = occurredAt,
                balanceTransfer = MemberBalanceTransfer(bob, alice, MoneyAmount.ofCents(40)),
            )

        assertEquals(
            listOf(MemberDebtReduction(reduction, MoneyAmount.ofCents(40))),
            listOf(reduction, debt).projectMemberDebtReductions(alice),
        )
    }

    private fun expense(
        seed: String,
        paidBy: MemberEmail,
        occurredAt: Instant,
        transfer: Pair<MemberEmail, Long>,
        id: LedgerEventId = acceptedExpenseLedgerEventId(seed),
    ): AcceptedExpenseLedgerEvent =
        AcceptedExpenseLedgerEvent(
            id = id,
            group = groupId("group"),
            expense = expenseId(seed),
            paidBy = paidBy,
            occurredAt = occurredAt,
            transfers = setOf(MemberBalanceTransfer(transfer.first, paidBy, MoneyAmount.ofCents(transfer.second))),
        )
}
