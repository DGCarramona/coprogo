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
    fun `invoke should not use income from a later UTC day`() {
        val futureIncome = incomeEvent(amountInCents = 100).copy(occurredAt = Instant.parse("2026-04-04T00:00:00Z"))
        val persistence = InMemoryCashPoolWithdrawalPersistence(listOf(futureIncome))

        val exception =
            assertThrows<InsufficientCashPoolBalanceException> {
                runTest { useCase(persistence)(command(amountInCents = 1)) }
            }

        assertEquals(MoneyAmount.ZERO, exception.availableAmount)
        assertEquals(listOf(futureIncome), persistence.events)
    }

    @Test
    fun `invoke should reject a backdated withdrawal that makes a later withdrawal impossible`() {
        val income = incomeEvent(amountInCents = 100)
        val laterWithdrawal = withdrawalEvent(amountInCents = 90, occurredAt = "2026-04-04T12:00:00Z")
        val persistence = InMemoryCashPoolWithdrawalPersistence(listOf(income, laterWithdrawal))

        val error =
            assertThrows<InsufficientCashPoolBalanceException> {
                runTest { useCase(persistence)(command(amountInCents = 20)) }
            }

        assertEquals(MoneyAmount.ofCents(20), error.requestedAmount)
        assertEquals(MoneyAmount.ofCents(10), error.availableAmount)
        assertEquals(listOf(income, laterWithdrawal), persistence.events)
    }

    @Test
    fun `invoke should report the minimum availability across all later withdrawals`() {
        val income = incomeEvent(amountInCents = 100)
        val firstWithdrawal = withdrawalEvent(amountInCents = 90, occurredAt = "2026-04-04T12:00:00Z")
        val laterIncome =
            incomeEvent(amountInCents = 20).copy(
                id = ledgerEventId("later-income"),
                occurredAt = Instant.parse("2026-04-05T12:00:00Z"),
            )
        val secondWithdrawal =
            withdrawalEvent(amountInCents = 30, occurredAt = "2026-04-06T12:00:00Z")
                .copy(id = ledgerEventId("second-withdrawal"))
        val original = listOf(income, firstWithdrawal, laterIncome, secondWithdrawal)
        val persistence = InMemoryCashPoolWithdrawalPersistence(original)

        val error =
            assertThrows<InsufficientCashPoolBalanceException> {
                runTest { useCase(persistence)(command(amountInCents = 20)) }
            }

        assertEquals(MoneyAmount.ZERO, error.availableAmount)
        assertEquals(original, persistence.events)
    }

    @Test
    fun `invoke should use all income on the withdrawal UTC day even if its instant is later`() =
        runTest {
            val income = incomeEvent(amountInCents = 100).copy(occurredAt = Instant.parse("2026-04-03T23:59:59Z"))
            val persistence = InMemoryCashPoolWithdrawalPersistence(listOf(income))

            useCase(persistence)(command(amountInCents = 100))

            assertEquals(MoneyAmount.ofCents(100), (persistence.events.last() as CashPoolWithdrawalLedgerEvent).ownRevenueShareConsumed)
        }

    @Test
    fun `invoke should calculate a backdated withdrawal before later withdrawals`() =
        runTest {
            val income = incomeEvent(amountInCents = 100)
            val laterWithdrawal = withdrawalEvent(amountInCents = 40, occurredAt = "2026-04-04T12:00:00Z")
            val persistence = InMemoryCashPoolWithdrawalPersistence(listOf(income, laterWithdrawal))

            useCase(persistence)(command(amountInCents = 60))

            assertEquals(MoneyAmount.ofCents(60), (persistence.events.last() as CashPoolWithdrawalLedgerEvent).ownRevenueShareConsumed)
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

    private fun withdrawalEvent(
        amountInCents: Long,
        occurredAt: String,
    ): CashPoolWithdrawalLedgerEvent =
        CashPoolWithdrawalLedgerEvent(
            id = ledgerEventId("later-withdrawal"),
            group = groupId("group"),
            withdrawnBy = memberEmail("alice"),
            withdrawnAmount = MoneyAmount.ofCents(amountInCents),
            ownRevenueShareConsumed = MoneyAmount.ofCents(amountInCents),
            balanceTransfers = emptySet(),
            occurredAt = Instant.parse(occurredAt),
        )

    private class InMemoryCashPoolWithdrawalPersistence(
        initialEvents: List<LedgerEvent> = emptyList(),
    ) : CashPoolWithdrawalPersistence {
        val events = initialEvents.toMutableList()
        var appendAttempts = 0

        override suspend fun appendFromCurrentLedger(
            group: tech.justdev.domain.shared.valueobject.GroupId,
            build: suspend (List<LedgerEvent>) -> CashPoolWithdrawalLedgerEvent,
        ) {
            appendAttempts += 1
            events += build(events.filter { event -> event.group == group })
        }
    }
}
