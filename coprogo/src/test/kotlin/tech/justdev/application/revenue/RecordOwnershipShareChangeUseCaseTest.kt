package tech.justdev.application.revenue

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tech.justdev.application.support.InMemoryOwnershipShareTimelineRepository
import tech.justdev.domain.revenue.entity.OwnershipShareChange
import tech.justdev.domain.revenue.entity.OwnershipShareTimeline
import tech.justdev.domain.revenue.valueobject.OwnershipPercentage
import tech.justdev.domain.revenue.valueobject.OwnershipShare
import tech.justdev.testsupport.FixedOwnershipShareChangeIdGenerator
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.groupUuid
import tech.justdev.testsupport.memberId
import tech.justdev.testsupport.memberUuid
import tech.justdev.testsupport.ownershipShareChangeId
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class RecordOwnershipShareChangeUseCaseTest {
    @Test
    fun `invoke should create and persist a new ownership share timeline change`() {
        runTest {
            val repository = InMemoryOwnershipShareTimelineRepository()
            val useCase =
                RecordOwnershipShareChangeUseCase(
                    ownershipShareTimelineRepository = repository,
                    ownershipShareChangeIdGenerator = FixedOwnershipShareChangeIdGenerator(listOf(ownershipShareChangeId("change-1"))),
                )

            useCase(
                RecordOwnershipShareChangeCommand(
                    group = groupUuid("group-1"),
                    effectiveDate = LocalDate.parse("2026-01-01"),
                    recordedBy = memberUuid("owner"),
                    recordedAt = Instant.parse("2026-04-03T10:00:00Z"),
                    shares =
                        setOf(
                            RecordOwnershipShareCommand(member = memberUuid("alice"), percentage = BigDecimal("60.00")),
                            RecordOwnershipShareCommand(member = memberUuid("bob"), percentage = BigDecimal("40.00")),
                        ),
                ),
            )

            assertEquals(
                OwnershipShareTimeline(
                    group = groupId("group-1"),
                    changes =
                        listOf(
                            OwnershipShareChange(
                                id = ownershipShareChangeId("change-1"),
                                effectiveDate = LocalDate.parse("2026-01-01"),
                                recordedBy = memberId("owner"),
                                recordedAt = Instant.parse("2026-04-03T10:00:00Z"),
                                shares =
                                    setOf(
                                        OwnershipShare(memberId("alice"), OwnershipPercentage.ofBasisPoints(6000)),
                                        OwnershipShare(memberId("bob"), OwnershipPercentage.ofBasisPoints(4000)),
                                    ),
                            ),
                        ),
                ),
                repository.findByGroup(groupId("group-1")),
            )
        }
    }
}
