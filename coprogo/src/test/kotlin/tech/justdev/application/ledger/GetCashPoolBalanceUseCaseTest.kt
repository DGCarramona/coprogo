package tech.justdev.application.ledger

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.application.support.InMemoryGroupRepository
import tech.justdev.application.support.InMemoryLedgerEventRepository
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.ledger.effect.MemberBalanceTransfer
import tech.justdev.domain.ledger.effect.MemberCashPoolShareDelta
import tech.justdev.domain.ledger.event.CashPoolIncomeLedgerEvent
import tech.justdev.domain.ledger.event.CashPoolWithdrawalLedgerEvent
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.groupUuid
import tech.justdev.testsupport.ledgerEventId
import tech.justdev.testsupport.memberEmail
import java.time.Instant

class GetCashPoolBalanceUseCaseTest {
    @Test
    fun `invoke should project the available common cash pool balance from the immutable ledger`() {
        runTest {
            val useCase =
                GetCashPoolBalanceUseCase(
                    groupAccessPolicy = GroupAccessPolicy(InMemoryGroupRepository(listOf(testGroup()))),
                    ledgerEventRepository =
                        InMemoryLedgerEventRepository(
                            events =
                                listOf(
                                    CashPoolIncomeLedgerEvent(
                                        id = ledgerEventId("cash-pool-income-1"),
                                        group = groupId("group-1"),
                                        amount = MoneyAmount.ofCents(100),
                                        allocations =
                                            setOf(
                                                MemberCashPoolShareDelta.increase(
                                                    member = memberEmail("alice"),
                                                    amount = MoneyAmount.ofCents(100),
                                                ),
                                            ),
                                        occurredAt = Instant.parse("2026-04-03T10:00:00Z"),
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
                CashPoolBalanceSnapshot(
                    group = groupId("group-1"),
                    availableAmountInCents = 65,
                ),
                useCase(GetCashPoolBalanceQuery(group = groupId("group-1"), requestedBy = memberEmail("alice"))),
            )
        }
    }

    private fun testGroup(): Group =
        Group.create(
            id = groupId("group-1"),
            createdBy = memberEmail("alice"),
            createdAt = Instant.parse("2026-04-03T09:00:00Z"),
        )
}
