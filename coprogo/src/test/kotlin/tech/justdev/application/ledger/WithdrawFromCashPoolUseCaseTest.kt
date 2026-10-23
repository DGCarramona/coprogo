package tech.justdev.application.ledger

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.application.group.GroupAccessDeniedException
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.application.support.InMemoryGroupRepository
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.ledger.event.CashPoolIncomeLedgerEvent
import tech.justdev.domain.ledger.event.CashPoolWithdrawalLedgerEvent
import tech.justdev.domain.ledger.event.LedgerEvent
import tech.justdev.domain.revenue.valueobject.OwnershipPercentage
import tech.justdev.domain.revenue.valueobject.OwnershipShare
import tech.justdev.domain.revenue.valueobject.RevenueDistribution
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.testsupport.FixedLedgerEventIdGenerator
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.ledgerEventId
import tech.justdev.testsupport.memberEmail
import java.time.Instant

class WithdrawFromCashPoolUseCaseTest {
    @Test
    fun `invoke should append a withdrawal built from the ledger protected by the persistence port`() =
        runTest {
            val income = incomeEvent(amountInCents = 100)
            val persistence = InMemoryCashPoolWithdrawalPersistence(listOf(income))

            useCase(persistence)(command(amountInCents = 100))

            assertEquals(
                listOf(
                    income,
                    CashPoolWithdrawalLedgerEvent(
                        id = ledgerEventId("withdrawal"),
                        group = groupId("group"),
                        withdrawnBy = memberEmail("alice"),
                        withdrawnAmount = MoneyAmount.ofCents(100),
                        ownRevenueShareConsumed = MoneyAmount.ofCents(100),
                        balanceTransfers = emptySet(),
                        occurredAt = Instant.parse("2026-04-03T12:00:00Z"),
                    ),
                ),
                persistence.events,
            )
        }

    @Test
    fun `invoke should reject a withdrawal larger than available cash`() {
        val persistence = InMemoryCashPoolWithdrawalPersistence(listOf(incomeEvent(amountInCents = 100)))

        val exception =
            assertThrows<InsufficientCashPoolBalanceException> {
                runTest { useCase(persistence)(command(amountInCents = 101)) }
            }

        assertEquals(MoneyAmount.ofCents(101), exception.requestedAmount)
        assertEquals(MoneyAmount.ofCents(100), exception.availableAmount)
        assertEquals(1, persistence.events.size)
    }

    @Test
    fun `invoke should reject a withdrawal from an empty cash pool`() {
        val persistence = InMemoryCashPoolWithdrawalPersistence()

        val exception =
            assertThrows<InsufficientCashPoolBalanceException> {
                runTest { useCase(persistence)(command(amountInCents = 1)) }
            }

        assertEquals(MoneyAmount.ofCents(1), exception.requestedAmount)
        assertEquals(MoneyAmount.ZERO, exception.availableAmount)
        assertEquals(emptyList<LedgerEvent>(), persistence.events)
    }

    @Test
    fun `invoke should check access before accessing the persistence port`() {
        val persistence = InMemoryCashPoolWithdrawalPersistence(listOf(incomeEvent(amountInCents = 100)))
        val useCase =
            WithdrawFromCashPoolUseCaseImpl(
                groupAccessPolicy = GroupAccessPolicy(InMemoryGroupRepository(listOf(group()))),
                cashPoolWithdrawalPersistence = persistence,
                ledgerEventIdGenerator = FixedLedgerEventIdGenerator(listOf(ledgerEventId("withdrawal"))),
            )

        assertThrows<GroupAccessDeniedException> {
            runTest { useCase(command(amountInCents = 10, withdrawnBy = memberEmail("outsider"))) }
        }

        assertEquals(0, persistence.appendAttempts)
    }

    private fun useCase(persistence: InMemoryCashPoolWithdrawalPersistence): WithdrawFromCashPoolUseCase =
        WithdrawFromCashPoolUseCaseImpl(
            groupAccessPolicy = GroupAccessPolicy(InMemoryGroupRepository(listOf(group()))),
            cashPoolWithdrawalPersistence = persistence,
            ledgerEventIdGenerator = FixedLedgerEventIdGenerator(listOf(ledgerEventId("withdrawal"))),
        )

    private fun command(
        amountInCents: Long,
        withdrawnBy: tech.justdev.domain.group.valueobject.MemberEmail = memberEmail("alice"),
    ): WithdrawFromCashPoolCommand =
        WithdrawFromCashPoolCommand(
            group = groupId("group"),
            withdrawnBy = withdrawnBy,
            amount = MoneyAmount.ofCents(amountInCents),
            occurredAt = Instant.parse("2026-04-03T12:00:00Z"),
        )

    private fun group(): Group = Group.create(groupId("group"), memberEmail("alice"), Instant.parse("2026-01-01T08:00:00Z"))

    private fun incomeEvent(amountInCents: Long): CashPoolIncomeLedgerEvent =
        CashPoolIncomeLedgerEvent.from(
            id = ledgerEventId("income"),
            group = groupId("group"),
            occurredAt = Instant.parse("2026-04-03T10:00:00Z"),
            distribution =
                RevenueDistribution.distribute(
                    totalAmount = MoneyAmount.ofCents(amountInCents),
                    ownershipShares = setOf(OwnershipShare(memberEmail("alice"), OwnershipPercentage.ofBasisPoints(10_000))),
                ),
        )

    private class InMemoryCashPoolWithdrawalPersistence(
        initialEvents: List<LedgerEvent> = emptyList(),
    ) : CashPoolWithdrawalPersistence {
        val events = initialEvents.toMutableList()
        var appendAttempts = 0

        override suspend fun appendFromCurrentLedger(
            group: tech.justdev.domain.shared.valueobject.GroupId,
            build: suspend (List<LedgerEvent>) -> tech.justdev.domain.ledger.event.CashPoolWithdrawalLedgerEvent,
        ) {
            appendAttempts += 1
            events += build(events.filter { event -> event.group == group })
        }
    }
}
