package tech.justdev.application.ledger

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.application.support.InMemoryGroupRepository
import tech.justdev.application.support.InMemoryLedgerEventRepository
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.ledger.effect.MemberBalanceTransfer
import tech.justdev.domain.ledger.event.AcceptedExpenseLedgerEvent
import tech.justdev.domain.ledger.event.CashPoolIncomeLedgerEvent
import tech.justdev.domain.ledger.event.CashPoolWithdrawalLedgerEvent
import tech.justdev.domain.revenue.valueobject.OwnershipPercentage
import tech.justdev.domain.revenue.valueobject.OwnershipShare
import tech.justdev.domain.revenue.valueobject.RevenueDistribution
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.testsupport.acceptedExpenseLedgerEventId
import tech.justdev.testsupport.expenseId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.ledgerEventId
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.memberEmailString
import java.time.Instant

class GetGroupBalancesUseCaseTest {
    @Test
    fun `invoke recalculates withdrawal transfers from chronological income shares`() =
        runTest {
            val income =
                CashPoolIncomeLedgerEvent.from(
                    id = ledgerEventId("income"),
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
                )
            val withdrawal =
                CashPoolWithdrawalLedgerEvent(
                    id = ledgerEventId("withdrawal"),
                    group = groupId("group-1"),
                    withdrawnBy = memberEmail("alice"),
                    withdrawnAmount = MoneyAmount.ofCents(70),
                    ownRevenueShareConsumed = MoneyAmount.ofCents(70),
                    balanceTransfers = emptySet(),
                    occurredAt = Instant.parse("2026-04-03T12:00:00Z"),
                )
            val useCase =
                GetGroupBalancesUseCase(
                    groupAccessPolicy = GroupAccessPolicy(InMemoryGroupRepository(listOf(testGroup()))),
                    ledgerEventRepository = InMemoryLedgerEventRepository(listOf(withdrawal, income)),
                )

            assertEquals(
                GroupBalancesSnapshot(
                    group = groupId("group-1"),
                    balances =
                        listOf(
                            GroupMemberBalanceSnapshot(memberEmailString("alice"), -10),
                            GroupMemberBalanceSnapshot(memberEmailString("bob"), 10),
                        ),
                ),
                useCase(GetGroupBalancesQuery(group = groupId("group-1"), requestedBy = memberEmail("alice"))),
            )
        }

    @Test
    fun `invoke should project sorted balances from the immutable ledger`() {
        runTest {
            val useCase =
                GetGroupBalancesUseCase(
                    groupAccessPolicy = GroupAccessPolicy(InMemoryGroupRepository(listOf(testGroup()))),
                    ledgerEventRepository =
                        InMemoryLedgerEventRepository(
                            events =
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
                                                MemberBalanceTransfer(memberEmail("carol"), memberEmail("alice"), MoneyAmount.ofCents(20)),
                                            ),
                                    ),
                                ),
                        ),
                )

            val snapshot = useCase(GetGroupBalancesQuery(group = groupId("group-1"), requestedBy = memberEmail("alice")))

            assertEquals(
                GroupBalancesSnapshot(
                    group = groupId("group-1"),
                    balances =
                        listOf(
                            GroupMemberBalanceSnapshot(memberEmailString("alice"), 50),
                            GroupMemberBalanceSnapshot(memberEmailString("bob"), -30),
                            GroupMemberBalanceSnapshot(memberEmailString("carol"), -20),
                        ),
                ),
                snapshot,
            )
        }
    }

    private fun testGroup(): Group =
        Group
            .create(
                id = groupId("group-1"),
                createdBy = memberEmail("alice"),
                createdAt = Instant.parse("2026-04-03T09:00:00Z"),
            ).addMember(
                member = memberEmail("bob"),
                joinedAt = Instant.parse("2026-04-03T09:01:00Z"),
            ).addMember(
                member = memberEmail("carol"),
                joinedAt = Instant.parse("2026-04-03T09:02:00Z"),
            )
}
