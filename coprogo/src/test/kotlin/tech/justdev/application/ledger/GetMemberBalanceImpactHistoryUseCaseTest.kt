package tech.justdev.application.ledger

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.application.group.GroupAccessDeniedException
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.application.support.InMemoryGroupRepository
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.ledger.effect.MemberBalanceTransfer
import tech.justdev.domain.ledger.event.AcceptedExpenseLedgerEvent
import tech.justdev.domain.ledger.event.AcceptedReimbursementLedgerEvent
import tech.justdev.domain.ledger.event.CashPoolIncomeLedgerEvent
import tech.justdev.domain.ledger.event.CashPoolWithdrawalLedgerEvent
import tech.justdev.domain.ledger.event.LedgerEvent
import tech.justdev.domain.ledger.projection.MemberBalanceImpact
import tech.justdev.domain.ledger.projection.MemberBalanceImpactType
import tech.justdev.domain.ledger.repository.LedgerEventRepository
import tech.justdev.domain.reimbursement.valueobject.ReimbursementId
import tech.justdev.domain.revenue.valueobject.OwnershipPercentage
import tech.justdev.domain.revenue.valueobject.OwnershipShare
import tech.justdev.domain.revenue.valueobject.RevenueDistribution
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.testsupport.acceptedExpenseLedgerEventId
import tech.justdev.testsupport.expenseId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.ledgerEventId
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.testUuid
import java.time.Instant

class GetMemberBalanceImpactHistoryUseCaseTest {
    @Test
    fun `invoke explains a withdrawal using its recalculated chronological transfers`() =
        runTest {
            val alice = memberEmail("alice")
            val bob = memberEmail("bob")
            val credit = expense(alice, bob, 100, Instant.parse("2026-04-03T09:00:00Z"))
            val income =
                CashPoolIncomeLedgerEvent.from(
                    id = ledgerEventId("income"),
                    group = GROUP,
                    occurredAt = Instant.parse("2026-04-03T10:00:00Z"),
                    distribution =
                        RevenueDistribution.distribute(
                            MoneyAmount.ofCents(100),
                            setOf(OwnershipShare(bob, OwnershipPercentage.ofBasisPoints(10_000))),
                        ),
                )
            val withdrawal =
                CashPoolWithdrawalLedgerEvent(
                    id = ledgerEventId("withdrawal"),
                    group = GROUP,
                    withdrawnBy = alice,
                    withdrawnAmount = MoneyAmount.ofCents(50),
                    ownRevenueShareConsumed = MoneyAmount.ofCents(50),
                    balanceTransfers = emptySet(),
                    occurredAt = Instant.parse("2026-04-03T12:00:00Z"),
                )
            val recalculated =
                withdrawal.copy(
                    ownRevenueShareConsumed = MoneyAmount.ZERO,
                    balanceTransfers = setOf(MemberBalanceTransfer(alice, bob, MoneyAmount.ofCents(50))),
                )

            assertEquals(
                listOf(MemberBalanceImpact(recalculated, MoneyAmount.ofCents(50), MemberBalanceImpactType.CREDIT_REDUCTION)),
                useCase(InMemoryLedgerEventRepository(listOf(credit, income, withdrawal)))(
                    GetMemberBalanceImpactHistoryQuery(GROUP, alice, alice),
                ),
            )
        }

    @Test
    fun `invoke should return authorized member debt impacts from newest to oldest`() =
        runTest {
            val alice = memberEmail("alice")
            val bob = memberEmail("bob")
            val debt = expense(bob, alice, 100, Instant.parse("2026-04-03T09:00:00Z"))
            val firstReduction = reimbursement(bob, alice, 30, Instant.parse("2026-04-03T10:00:00Z"))
            val latestReduction = reimbursement(bob, alice, 20, Instant.parse("2026-04-03T11:00:00Z"))
            val repository = InMemoryLedgerEventRepository(listOf(debt, firstReduction, latestReduction))

            val history = useCase(repository)(GetMemberBalanceImpactHistoryQuery(GROUP, alice, alice))

            assertEquals(
                listOf(
                    MemberBalanceImpact(latestReduction, MoneyAmount.ofCents(20)),
                    MemberBalanceImpact(firstReduction, MoneyAmount.ofCents(30)),
                ),
                history,
            )
        }

    @Test
    fun `invoke should check requester access before reading ledger events`() {
        val repository = InMemoryLedgerEventRepository()

        assertThrows<GroupAccessDeniedException> {
            runTest {
                useCase(repository)(
                    GetMemberBalanceImpactHistoryQuery(
                        group = GROUP,
                        member = memberEmail("alice"),
                        requestedBy = memberEmail("outsider"),
                    ),
                )
            }
        }

        assertEquals(0, repository.findAttempts)
    }

    @Test
    fun `invoke should reject history for a member outside the group before reading ledger events`() {
        val repository = InMemoryLedgerEventRepository()

        assertThrows<IllegalArgumentException> {
            runTest {
                useCase(repository)(
                    GetMemberBalanceImpactHistoryQuery(
                        group = GROUP,
                        member = memberEmail("outsider"),
                        requestedBy = memberEmail("alice"),
                    ),
                )
            }
        }

        assertEquals(0, repository.findAttempts)
    }

    private fun useCase(repository: LedgerEventRepository): GetMemberBalanceImpactHistoryUseCase =
        GetMemberBalanceImpactHistoryUseCaseImpl(
            groupAccessPolicy =
                GroupAccessPolicy(
                    InMemoryGroupRepository(
                        listOf(Group.create(GROUP, memberEmail("alice"), Instant.parse("2026-01-01T08:00:00Z"))),
                    ),
                ),
            ledgerEventRepository = repository,
        )

    private fun expense(
        paidBy: MemberEmail,
        participant: MemberEmail,
        amountInCents: Long,
        occurredAt: Instant,
    ): AcceptedExpenseLedgerEvent =
        AcceptedExpenseLedgerEvent(
            id = acceptedExpenseLedgerEventId("debt"),
            group = GROUP,
            expense = expenseId("debt"),
            paidBy = paidBy,
            occurredAt = occurredAt,
            transfers = setOf(MemberBalanceTransfer(participant, paidBy, MoneyAmount.ofCents(amountInCents))),
        )

    private fun reimbursement(
        from: MemberEmail,
        to: MemberEmail,
        amountInCents: Long,
        occurredAt: Instant,
    ): AcceptedReimbursementLedgerEvent =
        AcceptedReimbursementLedgerEvent(
            id = ledgerEventId("reimbursement-$amountInCents"),
            group = GROUP,
            reimbursement = ReimbursementId(testUuid("reimbursement-$amountInCents")),
            occurredAt = occurredAt,
            balanceTransfer = MemberBalanceTransfer(from, to, MoneyAmount.ofCents(amountInCents)),
        )

    private class InMemoryLedgerEventRepository(
        private val events: List<LedgerEvent> = emptyList(),
    ) : LedgerEventRepository {
        var findAttempts = 0

        override suspend fun append(event: LedgerEvent) = error("not used")

        override suspend fun findByGroup(group: GroupId): List<LedgerEvent> {
            findAttempts += 1
            return events.filter { event -> event.group == group }
        }
    }

    private companion object {
        val GROUP = groupId("group")
    }
}
