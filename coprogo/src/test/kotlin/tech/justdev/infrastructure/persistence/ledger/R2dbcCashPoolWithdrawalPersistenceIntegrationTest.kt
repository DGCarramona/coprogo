package tech.justdev.infrastructure.persistence.ledger

import jakarta.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import tech.justdev.application.ledger.CashPoolWithdrawalPersistence
import tech.justdev.application.ledger.InsufficientCashPoolBalanceException
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.domain.ledger.event.CashPoolIncomeLedgerEvent
import tech.justdev.domain.ledger.event.CashPoolWithdrawalLedgerEvent
import tech.justdev.domain.ledger.event.LedgerEvent
import tech.justdev.domain.ledger.projection.projectCashPoolBalance
import tech.justdev.domain.ledger.projection.projectMemberCashPoolShares
import tech.justdev.domain.ledger.repository.LedgerEventRepository
import tech.justdev.domain.revenue.valueobject.OwnershipPercentage
import tech.justdev.domain.revenue.valueobject.OwnershipShare
import tech.justdev.domain.revenue.valueobject.RevenueDistribution
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.testsupport.PostgresMicronautTest
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.ledgerEventId
import tech.justdev.testsupport.memberEmail
import java.time.Instant

@PostgresMicronautTest
class R2dbcCashPoolWithdrawalPersistenceIntegrationTest {
    @Inject
    lateinit var persistence: CashPoolWithdrawalPersistence

    @Inject
    lateinit var ledgerEventRepository: LedgerEventRepository

    @Inject
    lateinit var memberRepository: MemberRepository

    @Inject
    lateinit var groupRepository: GroupRepository

    @Test
    fun `serializes withdrawals so concurrent commands cannot spend the same balance`() =
        runTest {
            val owner = memberEmail("concurrent-withdrawal-owner")
            val group = groupId("concurrent-withdrawal-group")
            memberRepository.persist(Member(owner, INCOME_AT.minusSeconds(60)))
            groupRepository.persist(Group.create(group, owner, INCOME_AT.minusSeconds(30)))
            ledgerEventRepository.append(income(group, owner))

            val firstBuilderEntered = CompletableDeferred<Unit>()
            val releaseFirstBuilder = CompletableDeferred<Unit>()
            val secondInvocationStarted = CompletableDeferred<Unit>()
            val secondBuilderEntered = CompletableDeferred<Unit>()

            val first =
                async {
                    persistence.appendFromCurrentLedger(group) { events ->
                        firstBuilderEntered.complete(Unit)
                        releaseFirstBuilder.await()
                        withdrawal(events, "first", group, owner)
                    }
                }
            firstBuilderEntered.await()

            val second =
                async {
                    secondInvocationStarted.complete(Unit)
                    runCatching {
                        persistence.appendFromCurrentLedger(group) { events ->
                            secondBuilderEntered.complete(Unit)
                            withdrawal(events, "second", group, owner)
                        }
                    }
                }
            secondInvocationStarted.await()
            delay(200)

            assertFalse(secondBuilderEntered.isCompleted)
            releaseFirstBuilder.complete(Unit)
            first.await()
            val secondResult = second.await()

            assertEquals(InsufficientCashPoolBalanceException::class, secondResult.exceptionOrNull()?.javaClass?.kotlin)
            assertEquals(
                listOf(CashPoolIncomeLedgerEvent::class, CashPoolWithdrawalLedgerEvent::class),
                ledgerEventRepository.findByGroup(group).map { event -> event::class },
            )
        }

    private fun withdrawal(
        events: List<LedgerEvent>,
        seed: String,
        group: tech.justdev.domain.shared.valueobject.GroupId,
        owner: tech.justdev.domain.group.valueobject.MemberEmail,
    ): CashPoolWithdrawalLedgerEvent {
        val withdrawnAmount = MoneyAmount.ofCents(60)
        val availableAmount = MoneyAmount.ofCents(events.projectCashPoolBalance().inCents())
        if (withdrawnAmount > availableAmount) {
            throw InsufficientCashPoolBalanceException(withdrawnAmount, availableAmount)
        }

        return CashPoolWithdrawalLedgerEvent.from(
            id = ledgerEventId("concurrent-withdrawal-$seed"),
            group = group,
            withdrawnBy = owner,
            withdrawnAmount = withdrawnAmount,
            occurredAt = WITHDRAWN_AT,
            memberCashPoolShares = events.projectMemberCashPoolShares(),
        )
    }

    private fun income(
        group: tech.justdev.domain.shared.valueobject.GroupId,
        owner: tech.justdev.domain.group.valueobject.MemberEmail,
    ): CashPoolIncomeLedgerEvent =
        CashPoolIncomeLedgerEvent.from(
            id = ledgerEventId("concurrent-withdrawal-income"),
            group = group,
            occurredAt = INCOME_AT,
            distribution =
                RevenueDistribution.distribute(
                    totalAmount = MoneyAmount.ofCents(100),
                    ownershipShares = setOf(OwnershipShare(owner, OwnershipPercentage.ofBasisPoints(10_000))),
                ),
        )

    private companion object {
        val INCOME_AT: Instant = Instant.parse("2026-04-03T10:00:00Z")
        val WITHDRAWN_AT: Instant = Instant.parse("2026-04-03T12:00:00Z")
    }
}
