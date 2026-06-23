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
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.testsupport.acceptedExpenseLedgerEventId
import tech.justdev.testsupport.expenseId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.groupUuid
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.memberEmailString
import java.time.Instant

class GetGroupBalancesUseCaseTest {
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
