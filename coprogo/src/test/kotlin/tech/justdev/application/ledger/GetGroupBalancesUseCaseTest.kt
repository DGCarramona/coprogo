package tech.justdev.application.ledger

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tech.justdev.application.support.InMemoryLedgerEventRepository
import tech.justdev.domain.ledger.effect.MemberBalanceTransfer
import tech.justdev.domain.ledger.event.AcceptedExpenseLedgerEvent
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.testsupport.acceptedExpenseLedgerEventId
import tech.justdev.testsupport.expenseId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.groupUuid
import tech.justdev.testsupport.memberId
import tech.justdev.testsupport.memberUuid
import java.time.Instant

class GetGroupBalancesUseCaseTest {
    @Test
    fun `invoke should project sorted balances from the immutable ledger`() {
        runTest {
            val useCase =
                GetGroupBalancesUseCase(
                    ledgerEventRepository =
                        InMemoryLedgerEventRepository(
                            events =
                                listOf(
                                    AcceptedExpenseLedgerEvent(
                                        id = acceptedExpenseLedgerEventId("expense-1"),
                                        group = groupId("group-1"),
                                        expense = expenseId("expense-1"),
                                        paidBy = memberId("alice"),
                                        occurredAt = Instant.parse("2026-04-03T10:00:00Z"),
                                        transfers =
                                            setOf(
                                                MemberBalanceTransfer(memberId("bob"), memberId("alice"), MoneyAmount.ofCents(30)),
                                                MemberBalanceTransfer(memberId("carol"), memberId("alice"), MoneyAmount.ofCents(20)),
                                            ),
                                    ),
                                ),
                        ),
                )

            val snapshot = useCase(GetGroupBalancesQuery(group = groupUuid("group-1")))

            assertEquals(
                GroupBalancesSnapshot(
                    group = groupUuid("group-1"),
                    balances =
                        listOf(
                            GroupMemberBalanceSnapshot(memberUuid("alice"), 50),
                            GroupMemberBalanceSnapshot(memberUuid("bob"), -30),
                            GroupMemberBalanceSnapshot(memberUuid("carol"), -20),
                        ),
                ),
                snapshot,
            )
        }
    }
}
