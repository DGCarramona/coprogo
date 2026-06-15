package tech.justdev.application.revenue

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.application.group.OwnershipShareChangeForbiddenException
import tech.justdev.application.support.InMemoryGroupRepository
import tech.justdev.application.support.InMemoryOwnershipShareTimelineRepository
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.revenue.entity.OwnershipShareChange
import tech.justdev.domain.revenue.entity.OwnershipShareTimeline
import tech.justdev.domain.revenue.valueobject.OwnershipPercentage
import tech.justdev.domain.revenue.valueobject.OwnershipShare
import tech.justdev.testsupport.FixedOwnershipShareChangeIdGenerator
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.groupUuid
import tech.justdev.testsupport.memberEmail
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
                    groupAccessPolicy =
                        GroupAccessPolicy(
                            InMemoryGroupRepository(
                                groups =
                                    listOf(
                                        Group.create(
                                            id = groupId("group-1"),
                                            createdBy = memberEmail("owner"),
                                            createdAt = Instant.parse("2026-01-01T08:00:00Z"),
                                        ),
                                    ),
                            ),
                        ),
                    ownershipShareTimelineRepository = repository,
                    ownershipShareChangeIdGenerator = FixedOwnershipShareChangeIdGenerator(listOf(ownershipShareChangeId("change-1"))),
                )

            useCase(
                RecordOwnershipShareChangeCommand(
                    group = groupId("group-1"),
                    effectiveDate = LocalDate.parse("2026-01-01"),
                    recordedBy = memberEmail("owner"),
                    recordedAt = Instant.parse("2026-04-03T10:00:00Z"),
                    shares =
                        setOf(
                            RecordOwnershipShareCommand(member = memberEmail("alice"), percentage = BigDecimal("60.00")),
                            RecordOwnershipShareCommand(member = memberEmail("bob"), percentage = BigDecimal("40.00")),
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
                                recordedBy = memberEmail("owner"),
                                recordedAt = Instant.parse("2026-04-03T10:00:00Z"),
                                shares =
                                    setOf(
                                        OwnershipShare(memberEmail("alice"), OwnershipPercentage.ofBasisPoints(6000)),
                                        OwnershipShare(memberEmail("bob"), OwnershipPercentage.ofBasisPoints(4000)),
                                    ),
                            ),
                        ),
                ),
                repository.findByGroup(groupId("group-1")),
            )
        }
    }

    @Test
    fun `invoke should fail when a non creator attempts to change ownership shares`() {
        val useCase =
            RecordOwnershipShareChangeUseCase(
                groupAccessPolicy =
                    GroupAccessPolicy(
                        InMemoryGroupRepository(
                            groups =
                                listOf(
                                    Group.create(
                                        id = groupId("group-1"),
                                        createdBy = memberEmail("owner"),
                                        createdAt = Instant.parse("2026-01-01T08:00:00Z"),
                                    ),
                                ),
                        ),
                    ),
                ownershipShareTimelineRepository = InMemoryOwnershipShareTimelineRepository(),
                ownershipShareChangeIdGenerator = FixedOwnershipShareChangeIdGenerator(listOf(ownershipShareChangeId("change-1"))),
            )

        val error =
            assertThrows<OwnershipShareChangeForbiddenException> {
                runTest {
                    useCase(
                        RecordOwnershipShareChangeCommand(
                            group = groupId("group-1"),
                            effectiveDate = LocalDate.parse("2026-01-01"),
                            recordedBy = memberEmail("alice"),
                            recordedAt = Instant.parse("2026-04-03T10:00:00Z"),
                            shares =
                                setOf(
                                    RecordOwnershipShareCommand(member = memberEmail("alice"), percentage = BigDecimal("60.00")),
                                    RecordOwnershipShareCommand(member = memberEmail("bob"), percentage = BigDecimal("40.00")),
                                ),
                        ),
                    )
                }
            }

        assertEquals(
            "member alice@example.com is not allowed to modify ownership shares for group ${groupUuid("group-1")}",
            error.message,
        )
    }
}
