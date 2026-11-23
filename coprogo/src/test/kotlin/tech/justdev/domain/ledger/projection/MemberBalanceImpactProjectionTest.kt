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

class MemberBalanceImpactProjectionTest {
    @Test
    fun `projectMemberBalanceImpacts should explain actual debt impacts from every supported event`() {
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

        val impacts =
            listOf(withdrawal, compensatingExpense, initialDebt, reimbursement)
                .projectMemberBalanceImpacts(alice)

        assertEquals(
            listOf(
                reimbursement to 30L,
                compensatingExpense to 50L,
                withdrawal to 20L,
            ),
            impacts.map { impact -> impact.event to impact.amount.inCents() },
        )
    }

    @Test
    fun `projectMemberBalanceImpacts should omit transfers that do not reduce an existing debt`() {
        val alice = memberEmail("alice")
        val expense = expense("credit", alice, Instant.parse("2026-04-03T10:00:00Z"), memberEmail("bob") to 100)

        assertEquals(emptyList<MemberBalanceImpact>(), listOf(expense).projectMemberBalanceImpacts(alice))
    }

    @Test
    fun `projectMemberBalanceImpacts should include withdrawals reducing what others owe the withdrawer`() {
        val alice = memberEmail("alice")
        val bob = memberEmail("bob")
        val credit = expense("credit", alice, Instant.parse("2026-04-03T10:00:00Z"), bob to 100)
        val withdrawal =
            CashPoolWithdrawalLedgerEvent(
                id = ledgerEventId("withdrawal-reducing-credit"),
                group = groupId("group"),
                withdrawnBy = alice,
                withdrawnAmount = MoneyAmount.ofCents(120),
                ownRevenueShareConsumed = MoneyAmount.ZERO,
                balanceTransfers = setOf(MemberBalanceTransfer(alice, bob, MoneyAmount.ofCents(120))),
                occurredAt = Instant.parse("2026-04-03T11:00:00Z"),
            )
        val laterReimbursement =
            AcceptedReimbursementLedgerEvent(
                id = ledgerEventId("after-withdrawal"),
                group = groupId("group"),
                reimbursement = ReimbursementId(testUuid("after-withdrawal")),
                occurredAt = Instant.parse("2026-04-03T12:00:00Z"),
                balanceTransfer = MemberBalanceTransfer(bob, alice, MoneyAmount.ofCents(30)),
            )

        assertEquals(
            listOf(
                MemberBalanceImpact(withdrawal, MoneyAmount.ofCents(100), MemberBalanceImpactType.CREDIT_REDUCTION),
                MemberBalanceImpact(withdrawal, MoneyAmount.ofCents(20), MemberBalanceImpactType.DEBT_INCREASE),
                MemberBalanceImpact(laterReimbursement, MoneyAmount.ofCents(20), MemberBalanceImpactType.DEBT_REDUCTION),
            ),
            listOf(laterReimbursement, credit, withdrawal).projectMemberBalanceImpacts(alice),
        )
    }

    @Test
    fun `projectMemberBalanceImpacts should distinguish withdrawal effects for all affected members`() {
        val alice = memberEmail("alice")
        val bob = memberEmail("bob")
        val outsider = memberEmail("charlie")
        val debt = expense("debt-before-withdrawal", bob, Instant.parse("2026-04-03T09:00:00Z"), alice to 80)
        val otherDebt = expense("other-debt", outsider, Instant.parse("2026-04-03T09:30:00Z"), bob to 160)
        val withdrawal =
            CashPoolWithdrawalLedgerEvent(
                id = ledgerEventId("withdrawal-with-debt"),
                group = groupId("group"),
                withdrawnBy = alice,
                withdrawnAmount = MoneyAmount.ofCents(120),
                ownRevenueShareConsumed = MoneyAmount.ZERO,
                balanceTransfers = setOf(MemberBalanceTransfer(alice, bob, MoneyAmount.ofCents(120))),
                occurredAt = Instant.parse("2026-04-03T10:00:00Z"),
            )
        val events = listOf(debt, otherDebt, withdrawal)

        assertEquals(
            listOf(
                listOf(MemberBalanceImpact(withdrawal, MoneyAmount.ofCents(120), MemberBalanceImpactType.DEBT_INCREASE)),
                listOf(MemberBalanceImpact(withdrawal, MoneyAmount.ofCents(80), MemberBalanceImpactType.DEBT_REDUCTION)),
                emptyList(),
            ),
            listOf(alice, bob, outsider).map { member -> events.projectMemberBalanceImpacts(member) },
        )
    }

    @Test
    fun `projectMemberBalanceImpacts should omit withdrawals without transfers and handle exact credit`() {
        val alice = memberEmail("alice")
        val bob = memberEmail("bob")
        val credit = expense("exact-credit", alice, Instant.parse("2026-04-03T09:00:00Z"), bob to 100)
        val exactWithdrawal =
            CashPoolWithdrawalLedgerEvent(
                id = ledgerEventId("exact-withdrawal"),
                group = groupId("group"),
                withdrawnBy = alice,
                withdrawnAmount = MoneyAmount.ofCents(100),
                ownRevenueShareConsumed = MoneyAmount.ZERO,
                balanceTransfers = setOf(MemberBalanceTransfer(alice, bob, MoneyAmount.ofCents(100))),
                occurredAt = Instant.parse("2026-04-03T10:00:00Z"),
            )
        val ownShareWithdrawal =
            exactWithdrawal.copy(
                id = ledgerEventId("own-share-withdrawal"),
                ownRevenueShareConsumed = MoneyAmount.ofCents(100),
                balanceTransfers = emptySet(),
                occurredAt = Instant.parse("2026-04-03T11:00:00Z"),
            )

        assertEquals(
            listOf(MemberBalanceImpact(exactWithdrawal, MoneyAmount.ofCents(100), MemberBalanceImpactType.CREDIT_REDUCTION)),
            listOf(credit, exactWithdrawal, ownShareWithdrawal).projectMemberBalanceImpacts(alice),
        )
        assertEquals(
            listOf(MemberBalanceImpact(exactWithdrawal, MoneyAmount.ofCents(100), MemberBalanceImpactType.DEBT_INCREASE)),
            listOf(exactWithdrawal).projectMemberBalanceImpacts(alice),
        )
    }

    @Test
    fun `projectMemberBalanceImpacts should break equal timestamps by event id`() {
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
        val impact =
            AcceptedReimbursementLedgerEvent(
                id = LedgerEventId(UUID.fromString("00000000-0000-0000-0000-000000000002")),
                group = groupId("group"),
                reimbursement = ReimbursementId(testUuid("same-time-reimbursement")),
                occurredAt = occurredAt,
                balanceTransfer = MemberBalanceTransfer(bob, alice, MoneyAmount.ofCents(40)),
            )

        assertEquals(
            listOf(MemberBalanceImpact(impact, MoneyAmount.ofCents(40))),
            listOf(impact, debt).projectMemberBalanceImpacts(alice),
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
