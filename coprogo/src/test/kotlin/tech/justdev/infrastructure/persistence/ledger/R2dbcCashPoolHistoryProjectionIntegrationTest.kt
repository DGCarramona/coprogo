package tech.justdev.infrastructure.persistence.ledger

import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tech.justdev.application.ledger.CashPoolHistoryProjection
import tech.justdev.application.ledger.CashPoolIncomeAllocationSnapshot
import tech.justdev.application.ledger.CashPoolIncomeSnapshot
import tech.justdev.application.ledger.CashPoolWithdrawalSnapshot
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.ledger.effect.MemberBalanceTransfer
import tech.justdev.domain.ledger.event.CashPoolIncomeLedgerEvent
import tech.justdev.domain.ledger.event.CashPoolWithdrawalLedgerEvent
import tech.justdev.domain.ledger.repository.LedgerEventRepository
import tech.justdev.domain.revenue.valueobject.OwnershipPercentage
import tech.justdev.domain.revenue.valueobject.OwnershipShare
import tech.justdev.domain.revenue.valueobject.RevenueDistribution
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.testsupport.PostgresMicronautTest
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.ledgerEventId
import tech.justdev.testsupport.memberEmail
import java.time.Instant

@PostgresMicronautTest
class R2dbcCashPoolHistoryProjectionIntegrationTest {
    @Inject
    lateinit var projection: CashPoolHistoryProjection

    @Inject
    lateinit var ledgerEventRepository: LedgerEventRepository

    @Inject
    lateinit var memberRepository: MemberRepository

    @Inject
    lateinit var groupRepository: GroupRepository

    @Nested
    inner class FindByGroup {
        @Test
        fun `returns group-scoped incomes with their distribution and withdrawals`() =
            runTest {
                val fixture = persistGroup("cash-pool-history")
                val otherFixture = persistGroup("other-cash-pool-history")
                val income = fixture.income()
                val withdrawal = fixture.withdrawal()
                ledgerEventRepository.append(income)
                ledgerEventRepository.append(withdrawal)
                ledgerEventRepository.append(otherFixture.income())

                val result = projection.findByGroup(fixture.group)

                assertEquals(
                    listOf(
                        CashPoolIncomeSnapshot(
                            operation = income.id.toPrimitive(),
                            occurredAt = income.occurredAt,
                            amountInCents = 10_000,
                            allocations =
                                listOf(
                                    CashPoolIncomeAllocationSnapshot(fixture.coOwner.toPrimitive(), 4_000),
                                    CashPoolIncomeAllocationSnapshot(fixture.owner.toPrimitive(), 6_000),
                                ),
                        ),
                        CashPoolWithdrawalSnapshot(
                            operation = withdrawal.id.toPrimitive(),
                            occurredAt = withdrawal.occurredAt,
                            withdrawnBy = fixture.owner.toPrimitive(),
                            amountInCents = 7_000,
                            ownRevenueShareConsumedInCents = 6_000,
                        ),
                    ),
                    result,
                )
            }
    }

    private suspend fun persistGroup(seed: String): Fixture {
        val owner = memberEmail("$seed-owner")
        val coOwner = memberEmail("$seed-co-owner")
        memberRepository.persist(Member(owner, Instant.parse("2026-10-01T08:00:00Z")))
        memberRepository.persist(Member(coOwner, Instant.parse("2026-10-01T08:01:00Z")))
        val group = groupId("$seed-group")
        groupRepository.persist(
            Group
                .create(group, owner, Instant.parse("2026-10-01T08:02:00Z"))
                .addMember(coOwner, Instant.parse("2026-10-01T08:03:00Z")),
        )
        return Fixture(seed, group, owner, coOwner)
    }

    private data class Fixture(
        val seed: String,
        val group: GroupId,
        val owner: MemberEmail,
        val coOwner: MemberEmail,
    ) {
        fun income(): CashPoolIncomeLedgerEvent =
            CashPoolIncomeLedgerEvent.from(
                id = ledgerEventId("$seed-income"),
                group = group,
                occurredAt = Instant.parse("2026-10-01T10:00:00Z"),
                distribution =
                    RevenueDistribution.distribute(
                        MoneyAmount.ofCents(10_000),
                        setOf(
                            OwnershipShare(owner, OwnershipPercentage.ofBasisPoints(6_000)),
                            OwnershipShare(coOwner, OwnershipPercentage.ofBasisPoints(4_000)),
                        ),
                    ),
            )

        fun withdrawal(): CashPoolWithdrawalLedgerEvent =
            CashPoolWithdrawalLedgerEvent(
                id = ledgerEventId("$seed-withdrawal"),
                group = group,
                withdrawnBy = owner,
                withdrawnAmount = MoneyAmount.ofCents(7_000),
                ownRevenueShareConsumed = MoneyAmount.ofCents(6_000),
                balanceTransfers =
                    setOf(
                        MemberBalanceTransfer(
                            fromMember = owner,
                            toMember = coOwner,
                            amount = MoneyAmount.ofCents(1_000),
                        ),
                    ),
                occurredAt = Instant.parse("2026-10-02T10:00:00Z"),
            )
    }
}
