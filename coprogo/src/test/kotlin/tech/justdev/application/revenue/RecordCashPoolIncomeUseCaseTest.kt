package tech.justdev.application.revenue

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.application.group.GroupAccessDeniedException
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.application.support.InMemoryGroupRepository
import tech.justdev.application.support.InMemoryLedgerEventRepository
import tech.justdev.application.support.InMemoryOwnershipShareTimelineRepository
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.ledger.event.CashPoolIncomeLedgerEvent
import tech.justdev.domain.revenue.entity.OwnershipShareChange
import tech.justdev.domain.revenue.entity.OwnershipShareTimeline
import tech.justdev.domain.revenue.valueobject.OwnershipPercentage
import tech.justdev.domain.revenue.valueobject.OwnershipShare
import tech.justdev.domain.revenue.valueobject.RevenueDistribution
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.testsupport.FixedLedgerEventIdGenerator
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.ledgerEventId
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.ownershipShareChangeId
import java.time.Instant
import java.time.LocalDate

class RecordCashPoolIncomeUseCaseTest {
    @Test
    fun `invoke should append income ledger event with allocations using shares effective at income date`() =
        runTest {
            val ledgerEventRepository = InMemoryLedgerEventRepository()
            val useCase = useCase(ledgerEventRepository = ledgerEventRepository)

            useCase(
                RecordCashPoolIncomeCommand(
                    group = groupId("income-group"),
                    recordedBy = memberEmail("co-owner"),
                    amount = MoneyAmount.ofCents(10_001),
                    receivedAt = Instant.parse("2026-04-03T10:00:00Z"),
                    effectiveDate = LocalDate.parse("2026-04-01"),
                ),
            )

            assertEquals(
                listOf(
                    CashPoolIncomeLedgerEvent.from(
                        id = ledgerEventId("income-event"),
                        group = groupId("income-group"),
                        occurredAt = Instant.parse("2026-04-03T10:00:00Z"),
                        distribution =
                            RevenueDistribution.distribute(
                                totalAmount = MoneyAmount.ofCents(10_001),
                                ownershipShares =
                                    setOf(
                                        OwnershipShare(memberEmail("owner"), OwnershipPercentage.ofBasisPoints(6000)),
                                        OwnershipShare(memberEmail("co-owner"), OwnershipPercentage.ofBasisPoints(4000)),
                                    ),
                            ),
                    ),
                ),
                ledgerEventRepository.allEvents(),
            )
        }

    @Test
    fun `invoke should fail when the recorder is not a group member`() {
        val ledgerEventRepository = InMemoryLedgerEventRepository()
        val useCase = useCase(ledgerEventRepository = ledgerEventRepository)

        assertThrows<GroupAccessDeniedException> {
            runTest {
                useCase(
                    RecordCashPoolIncomeCommand(
                        group = groupId("income-group"),
                        recordedBy = memberEmail("outsider"),
                        amount = MoneyAmount.ofCents(10_001),
                        receivedAt = Instant.parse("2026-04-03T10:00:00Z"),
                        effectiveDate = LocalDate.parse("2026-04-01"),
                    ),
                )
            }
        }
        assertEquals(emptyList<Any>(), ledgerEventRepository.allEvents())
    }

    private fun useCase(ledgerEventRepository: InMemoryLedgerEventRepository): RecordCashPoolIncomeUseCase =
        RecordCashPoolIncomeUseCase(
            groupAccessPolicy = GroupAccessPolicy(InMemoryGroupRepository(listOf(group()))),
            ownershipShareTimelineRepository = InMemoryOwnershipShareTimelineRepository(listOf(timeline())),
            ledgerEventRepository = ledgerEventRepository,
            ledgerEventIdGenerator =
                FixedLedgerEventIdGenerator(
                    listOf(ledgerEventId("income-event")),
                ),
        )

    private fun group(): Group =
        Group
            .create(
                id = groupId("income-group"),
                createdBy = memberEmail("owner"),
                createdAt = Instant.parse("2026-01-01T08:00:00Z"),
            ).addMember(
                member = memberEmail("co-owner"),
                joinedAt = Instant.parse("2026-01-01T09:00:00Z"),
            )

    private fun timeline(): OwnershipShareTimeline =
        OwnershipShareTimeline(
            group = groupId("income-group"),
            changes =
                listOf(
                    OwnershipShareChange(
                        id = ownershipShareChangeId("income-shares"),
                        effectiveDate = LocalDate.parse("2026-01-01"),
                        recordedBy = memberEmail("owner"),
                        recordedAt = Instant.parse("2026-01-01T08:30:00Z"),
                        shares =
                            setOf(
                                OwnershipShare(memberEmail("owner"), OwnershipPercentage.ofBasisPoints(6000)),
                                OwnershipShare(memberEmail("co-owner"), OwnershipPercentage.ofBasisPoints(4000)),
                            ),
                    ),
                ),
        )
}
