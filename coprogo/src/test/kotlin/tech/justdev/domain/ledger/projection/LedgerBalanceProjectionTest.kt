package tech.justdev.domain.ledger.projection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tech.justdev.domain.ledger.effect.MemberBalanceTransfer
import tech.justdev.domain.ledger.event.AcceptedExpenseLedgerEvent
import tech.justdev.domain.ledger.event.CashPoolIncomeLedgerEvent
import tech.justdev.domain.ledger.event.CashPoolWithdrawalLedgerEvent
import tech.justdev.domain.ledger.valueobject.NetBalanceAmount
import tech.justdev.domain.revenue.valueobject.OwnershipPercentage
import tech.justdev.domain.revenue.valueobject.OwnershipShare
import tech.justdev.domain.revenue.valueobject.RevenueDistribution
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.testsupport.acceptedExpenseLedgerEventId
import tech.justdev.testsupport.expenseId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.ledgerEventId
import tech.justdev.testsupport.memberEmail
import java.time.Instant

class LedgerBalanceProjectionTest {
    @Test
    fun `projectMemberBalances should aggregate incoming and outgoing transfers into net balances`() {
        val balances =
            listOf(
                AcceptedExpenseLedgerEvent(
                    id = acceptedExpenseLedgerEventId("expense-1"),
                    group = groupId("group-1"),
                    expense = expenseId("expense-1"),
                    paidBy = memberEmail("alice"),
                    occurredAt = Instant.parse("2026-04-03T10:00:00Z"),
                    transfers =
                        setOf(
                            MemberBalanceTransfer(memberEmail("bob"), memberEmail("alice"), MoneyAmount.ofCents(30)),
                        ),
                ),
                AcceptedExpenseLedgerEvent(
                    id = acceptedExpenseLedgerEventId("expense-2"),
                    group = groupId("group-1"),
                    expense = expenseId("expense-2"),
                    paidBy = memberEmail("bob"),
                    occurredAt = Instant.parse("2026-04-03T11:00:00Z"),
                    transfers =
                        setOf(
                            MemberBalanceTransfer(memberEmail("alice"), memberEmail("bob"), MoneyAmount.ofCents(10)),
                        ),
                ),
            ).projectMemberBalances()

        assertEquals(
            setOf(
                MemberLedgerBalance(memberEmail("alice"), NetBalanceAmount.ofCents(20)),
                MemberLedgerBalance(memberEmail("bob"), NetBalanceAmount.ofCents(-20)),
            ),
            balances,
        )
    }

    @Test
    fun `projectMemberBalances should ignore cash-pool effects unless they explicitly reduce inter-member debt`() {
        val balances =
            listOf(
                CashPoolIncomeLedgerEvent.from(
                    id = ledgerEventId("cash-pool-income-1"),
                    group = groupId("group-1"),
                    occurredAt = Instant.parse("2026-04-03T10:00:00Z"),
                    distribution =
                        RevenueDistribution.distribute(
                            totalAmount = MoneyAmount.ofCents(100),
                            ownershipShares =
                                setOf(
                                    OwnershipShare(memberEmail("alice"), OwnershipPercentage.ofBasisPoints(6000)),
                                    OwnershipShare(memberEmail("bob"), OwnershipPercentage.ofBasisPoints(4000)),
                                ),
                        ),
                ),
                CashPoolWithdrawalLedgerEvent(
                    id = ledgerEventId("cash-pool-withdrawal-1"),
                    group = groupId("group-1"),
                    withdrawnBy = memberEmail("alice"),
                    withdrawnAmount = MoneyAmount.ofCents(35),
                    ownRevenueShareConsumed = MoneyAmount.ofCents(25),
                    balanceTransfers =
                        setOf(
                            MemberBalanceTransfer(memberEmail("alice"), memberEmail("bob"), MoneyAmount.ofCents(10)),
                        ),
                    occurredAt = Instant.parse("2026-04-03T12:00:00Z"),
                ),
            ).projectMemberBalances()

        assertEquals(
            setOf(
                MemberLedgerBalance(memberEmail("alice"), NetBalanceAmount.ofCents(-10)),
                MemberLedgerBalance(memberEmail("bob"), NetBalanceAmount.ofCents(10)),
            ),
            balances,
        )
    }
}
