package tech.justdev.domain.ledger.projection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tech.justdev.domain.ledger.effect.MemberBalanceTransfer
import tech.justdev.domain.ledger.event.CashPoolIncomeLedgerEvent
import tech.justdev.domain.ledger.event.CashPoolWithdrawalLedgerEvent
import tech.justdev.domain.ledger.event.AcceptedExpenseLedgerEvent
import tech.justdev.domain.ledger.event.RevenueDistributionLedgerEvent
import tech.justdev.domain.ledger.valueobject.NetBalanceAmount
import tech.justdev.domain.revenue.valueobject.OwnershipPercentage
import tech.justdev.domain.revenue.valueobject.OwnershipShare
import tech.justdev.domain.revenue.valueobject.RevenueDistribution
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.testsupport.acceptedExpenseLedgerEventId
import tech.justdev.testsupport.expenseId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.ledgerEventId
import tech.justdev.testsupport.memberId
import java.time.Instant

class LedgerBalanceProjectionTest {

    @Test
    fun `projectMemberBalances should aggregate incoming and outgoing transfers into net balances`() {
        val balances = listOf(
            AcceptedExpenseLedgerEvent(
                id = acceptedExpenseLedgerEventId("expense-1"),
                group = groupId("group-1"),
                expense = expenseId("expense-1"),
                paidBy = memberId("alice"),
                occurredAt = Instant.parse("2026-04-03T10:00:00Z"),
                transfers = setOf(
                    MemberBalanceTransfer(memberId("bob"), memberId("alice"), MoneyAmount.ofCents(30)),
                ),
            ),
            AcceptedExpenseLedgerEvent(
                id = acceptedExpenseLedgerEventId("expense-2"),
                group = groupId("group-1"),
                expense = expenseId("expense-2"),
                paidBy = memberId("bob"),
                occurredAt = Instant.parse("2026-04-03T11:00:00Z"),
                transfers = setOf(
                    MemberBalanceTransfer(memberId("alice"), memberId("bob"), MoneyAmount.ofCents(10)),
                ),
            ),
        ).projectMemberBalances()

        assertEquals(
            setOf(
                MemberLedgerBalance(memberId("alice"), NetBalanceAmount.ofCents(20)),
                MemberLedgerBalance(memberId("bob"), NetBalanceAmount.ofCents(-20)),
            ),
            balances,
        )
    }

    @Test
    fun `projectMemberBalances should ignore cash-pool effects unless they explicitly reduce inter-member debt`() {
        val balances = listOf(
            CashPoolIncomeLedgerEvent(
                id = ledgerEventId("cash-pool-income-1"),
                group = groupId("group-1"),
                amount = MoneyAmount.ofCents(100),
                occurredAt = Instant.parse("2026-04-03T10:00:00Z"),
            ),
            RevenueDistributionLedgerEvent.from(
                id = ledgerEventId("revenue-distribution-1"),
                group = groupId("group-1"),
                occurredAt = Instant.parse("2026-04-03T10:01:00Z"),
                distribution = RevenueDistribution.distribute(
                    totalAmount = MoneyAmount.ofCents(100),
                    ownershipShares = setOf(
                        OwnershipShare(memberId("alice"), OwnershipPercentage.ofBasisPoints(6000)),
                        OwnershipShare(memberId("bob"), OwnershipPercentage.ofBasisPoints(4000)),
                    ),
                ),
            ),
            CashPoolWithdrawalLedgerEvent(
                id = ledgerEventId("cash-pool-withdrawal-1"),
                group = groupId("group-1"),
                withdrawnBy = memberId("alice"),
                withdrawnAmount = MoneyAmount.ofCents(35),
                ownRevenueShareConsumed = MoneyAmount.ofCents(25),
                balanceTransfers = setOf(
                    MemberBalanceTransfer(memberId("alice"), memberId("bob"), MoneyAmount.ofCents(10)),
                ),
                occurredAt = Instant.parse("2026-04-03T12:00:00Z"),
            ),
        ).projectMemberBalances()

        assertEquals(
            setOf(
                MemberLedgerBalance(memberId("alice"), NetBalanceAmount.ofCents(-10)),
                MemberLedgerBalance(memberId("bob"), NetBalanceAmount.ofCents(10)),
            ),
            balances,
        )
    }
}
