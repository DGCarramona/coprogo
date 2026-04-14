package tech.justdev.application.ledger

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tech.justdev.application.support.InMemoryLedgerEventRepository
import tech.justdev.domain.ledger.effect.MemberBalanceTransfer
import tech.justdev.domain.ledger.event.CashPoolWithdrawalLedgerEvent
import tech.justdev.domain.ledger.event.RevenueDistributionLedgerEvent
import tech.justdev.domain.revenue.valueobject.OwnershipPercentage
import tech.justdev.domain.revenue.valueobject.OwnershipShare
import tech.justdev.domain.revenue.valueobject.RevenueDistribution
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.groupUuid
import tech.justdev.testsupport.ledgerEventId
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.memberEmailString
import java.time.Instant

class GetMemberCashPoolSharesUseCaseTest {
    @Test
    fun `invoke should project each member remaining cash-pool revenue share from the immutable ledger`() {
        runTest {
            val useCase =
                GetMemberCashPoolSharesUseCase(
                    ledgerEventRepository =
                        InMemoryLedgerEventRepository(
                            events =
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
                                                MemberBalanceTransfer(
                                                    fromMember = memberEmail("alice"),
                                                    toMember = memberEmail("bob"),
                                                    amount = MoneyAmount.ofCents(10),
                                                ),
                                            ),
                                        occurredAt = Instant.parse("2026-04-03T12:00:00Z"),
                                    ),
                                ),
                        ),
                )

            assertEquals(
                GroupMemberCashPoolSharesSnapshot(
                    group = groupUuid("group-1"),
                    shares =
                        listOf(
                            MemberCashPoolShareSnapshot(member = memberEmailString("alice"), amountInCents = 35),
                            MemberCashPoolShareSnapshot(member = memberEmailString("bob"), amountInCents = 40),
                        ),
                ),
                useCase(GetMemberCashPoolSharesQuery(group = groupUuid("group-1"))),
            )
        }
    }
}
