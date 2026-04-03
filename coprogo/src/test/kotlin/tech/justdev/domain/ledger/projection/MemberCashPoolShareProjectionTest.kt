package tech.justdev.domain.ledger.projection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tech.justdev.domain.ledger.effect.MemberBalanceTransfer
import tech.justdev.domain.ledger.event.CashPoolWithdrawalLedgerEvent
import tech.justdev.domain.ledger.event.RevenueDistributionLedgerEvent
import tech.justdev.domain.ledger.valueobject.NetBalanceAmount
import tech.justdev.domain.revenue.valueobject.OwnershipPercentage
import tech.justdev.domain.revenue.valueobject.OwnershipShare
import tech.justdev.domain.revenue.valueobject.RevenueDistribution
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.ledgerEventId
import tech.justdev.testsupport.memberId
import java.time.Instant

class MemberCashPoolShareProjectionTest {
    @Test
    fun `projectMemberCashPoolShares should track distributed revenue share that remains in the common cash pool`() {
        val shareBalances =
            listOf(
                RevenueDistributionLedgerEvent.from(
                    id = ledgerEventId("revenue-distribution-1"),
                    group = groupId("group-1"),
                    occurredAt = Instant.parse("2026-04-03T10:00:00Z"),
                    distribution =
                        RevenueDistribution.distribute(
                            totalAmount = MoneyAmount.ofCents(100),
                            ownershipShares =
                                setOf(
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
                    balanceTransfers =
                        setOf(
                            MemberBalanceTransfer(
                                fromMember = memberId("alice"),
                                toMember = memberId("bob"),
                                amount = MoneyAmount.ofCents(10),
                            ),
                        ),
                    occurredAt = Instant.parse("2026-04-03T12:00:00Z"),
                ),
            ).projectMemberCashPoolShares()

        assertEquals(
            setOf(
                MemberCashPoolShareBalance(member = memberId("alice"), amount = NetBalanceAmount.ofCents(35)),
                MemberCashPoolShareBalance(member = memberId("bob"), amount = NetBalanceAmount.ofCents(40)),
            ),
            shareBalances,
        )
    }
}
