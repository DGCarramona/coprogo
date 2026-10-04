package tech.justdev.infrastructure.persistence.ledger

import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.domain.ledger.effect.MemberBalanceTransfer
import tech.justdev.domain.ledger.event.AcceptedExpenseLedgerEvent
import tech.justdev.domain.ledger.event.AcceptedReimbursementLedgerEvent
import tech.justdev.domain.ledger.event.CashPoolIncomeLedgerEvent
import tech.justdev.domain.ledger.event.CashPoolWithdrawalLedgerEvent
import tech.justdev.domain.ledger.event.LedgerEvent
import tech.justdev.domain.ledger.repository.LedgerEventRepository
import tech.justdev.domain.reimbursement.entity.Reimbursement
import tech.justdev.domain.reimbursement.repository.ReimbursementRepository
import tech.justdev.domain.reimbursement.valueobject.ReimbursementId
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
class R2dbcLedgerEventRepositoryIntegrationTest {
    @Inject
    lateinit var ledgerEventRepository: LedgerEventRepository

    @Inject
    lateinit var memberRepository: MemberRepository

    @Inject
    lateinit var groupRepository: GroupRepository

    @Inject
    lateinit var reimbursementRepository: ReimbursementRepository

    @Nested
    inner class Append {
        @Test
        fun `should append cash-pool income and revenue distribution events`() =
            runTest {
                val fixture = persistedGroupFixture("la")
                val events = fixture.revenueEvents()

                events.forEach { event -> ledgerEventRepository.append(event) }

                assertEquals(events, ledgerEventRepository.findByGroup(fixture.group.id))
            }

        @Test
        fun `should append withdrawal events with compensation transfers`() =
            runTest {
                val fixture = persistedGroupFixture("lb")
                val withdrawal = fixture.withdrawalEvent()

                ledgerEventRepository.append(withdrawal)

                assertEquals(listOf(withdrawal), ledgerEventRepository.findByGroup(fixture.group.id))
            }

        @Test
        fun `should append accepted expense events with their balance transfers`() =
            runTest {
                val fixture = persistedGroupFixture("accepted-expense")
                val event =
                    AcceptedExpenseLedgerEvent(
                        id = ledgerEventId("${fixture.seed}-accepted-expense"),
                        group = fixture.group.id,
                        expense = ExpenseId(tech.justdev.testsupport.testUuid("${fixture.seed}-expense")),
                        paidBy = fixture.owner,
                        occurredAt = Instant.parse("2026-04-03T12:00:00Z"),
                        transfers =
                            setOf(
                                MemberBalanceTransfer(
                                    fromMember = fixture.coOwner,
                                    toMember = fixture.owner,
                                    amount = MoneyAmount.ofCents(45),
                                ),
                            ),
                    )

                ledgerEventRepository.append(event)

                assertEquals(listOf(event), ledgerEventRepository.findByGroup(fixture.group.id))
            }

        @Test
        fun `should append accepted reimbursement events with their debt reduction transfer`() =
            runTest {
                val fixture = persistedGroupFixture("accepted-reimbursement")
                val reimbursement = fixture.acceptedReimbursement()
                val event = AcceptedReimbursementLedgerEvent.from(reimbursement)
                reimbursementRepository.persist(reimbursement)

                ledgerEventRepository.append(event)

                assertEquals(listOf(event), ledgerEventRepository.findByGroup(fixture.group.id))
            }

        @Test
        fun `should allow only one ledger event for an accepted reimbursement`() =
            runTest {
                val fixture = persistedGroupFixture("unique-accepted-reimbursement")
                val reimbursement = fixture.acceptedReimbursement()
                val event = AcceptedReimbursementLedgerEvent.from(reimbursement)
                reimbursementRepository.persist(reimbursement)
                ledgerEventRepository.append(event)

                assertThrows<RuntimeException> {
                    runTest {
                        ledgerEventRepository.append(event.copy(id = ledgerEventId("duplicate-accepted-reimbursement")))
                    }
                }
                assertEquals(listOf(event), ledgerEventRepository.findByGroup(fixture.group.id))
            }
    }

    @Nested
    inner class FindByGroup {
        @Test
        fun `should return events for the requested group ordered by occurrence date`() =
            runTest {
                val firstGroup = persistedGroupFixture("lc")
                val secondGroup = persistedGroupFixture("ld")
                val laterEvent = firstGroup.incomeEvent("later", "2026-04-03T12:00:00Z", 100)
                val earlierEvent = firstGroup.incomeEvent("earlier", "2026-04-03T10:00:00Z", 50)
                val otherGroupEvent = secondGroup.incomeEvent("other", "2026-04-03T11:00:00Z", 75)

                listOf(laterEvent, earlierEvent, otherGroupEvent).forEach { event -> ledgerEventRepository.append(event) }

                assertEquals(listOf(earlierEvent, laterEvent), ledgerEventRepository.findByGroup(firstGroup.group.id))
            }

        @Test
        fun `should return an empty list when no event exists for the group`() =
            runTest {
                val fixture = persistedGroupFixture("le")

                assertEquals(emptyList<LedgerEvent>(), ledgerEventRepository.findByGroup(fixture.group.id))
            }
    }

    private suspend fun persistedGroupFixture(seed: String): LedgerEventFixture {
        val owner = memberEmail("$seed-owner")
        val coOwner = memberEmail("$seed-co-owner")
        memberRepository.persist(Member(email = owner, createdAt = Instant.parse("2026-04-03T08:00:00Z")))
        memberRepository.persist(Member(email = coOwner, createdAt = Instant.parse("2026-04-03T08:01:00Z")))

        val group =
            Group
                .create(
                    id = groupId("$seed-group"),
                    createdBy = owner,
                    createdAt = Instant.parse("2026-04-03T08:02:00Z"),
                ).addMember(
                    member = coOwner,
                    joinedAt = Instant.parse("2026-04-03T08:03:00Z"),
                )
        groupRepository.persist(group)

        return LedgerEventFixture(
            seed = seed,
            group = group,
        )
    }

    private data class LedgerEventFixture(
        val seed: String,
        val group: Group,
    ) {
        val owner = group.createdBy
        val coOwner = group.members.single { member -> member.member != owner }.member

        fun revenueEvents(): List<LedgerEvent> =
            listOf(
                incomeEvent("income", "2026-04-03T10:00:00Z", 101),
            )

        fun incomeEvent(
            eventSeed: String,
            occurredAt: String,
            amountInCents: Long,
        ): CashPoolIncomeLedgerEvent =
            CashPoolIncomeLedgerEvent.from(
                id = ledgerEventId("$seed-$eventSeed"),
                group = group.id,
                occurredAt = Instant.parse(occurredAt),
                distribution =
                    RevenueDistribution.distribute(
                        totalAmount = MoneyAmount.ofCents(amountInCents),
                        ownershipShares =
                            setOf(
                                OwnershipShare(owner, OwnershipPercentage.ofBasisPoints(6000)),
                                OwnershipShare(coOwner, OwnershipPercentage.ofBasisPoints(4000)),
                            ),
                    ),
            )

        fun withdrawalEvent(): CashPoolWithdrawalLedgerEvent =
            CashPoolWithdrawalLedgerEvent(
                id = ledgerEventId("$seed-withdrawal"),
                group = group.id,
                withdrawnBy = owner,
                withdrawnAmount = MoneyAmount.ofCents(80),
                ownRevenueShareConsumed = MoneyAmount.ofCents(60),
                balanceTransfers =
                    setOf(
                        MemberBalanceTransfer(
                            fromMember = owner,
                            toMember = coOwner,
                            amount = MoneyAmount.ofCents(20),
                        ),
                    ),
                occurredAt = Instant.parse("2026-04-03T12:00:00Z"),
            )

        fun acceptedReimbursement(): Reimbursement =
            Reimbursement.recordDirect(
                id = ReimbursementId(tech.justdev.testsupport.testUuid("$seed-reimbursement")),
                group = group.id,
                paidBy = coOwner,
                receivedBy = owner,
                amount = MoneyAmount.ofCents(45),
                reimbursedAt = Instant.parse("2026-04-03T11:00:00Z"),
                declaredBy = owner,
                declaredAt = Instant.parse("2026-04-03T12:00:00Z"),
            )
    }
}
