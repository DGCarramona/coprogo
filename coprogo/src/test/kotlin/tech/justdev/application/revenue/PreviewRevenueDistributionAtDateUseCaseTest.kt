package tech.justdev.application.revenue

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.application.support.InMemoryGroupRepository
import tech.justdev.application.support.InMemoryOwnershipShareTimelineRepository
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.revenue.entity.OwnershipShareChange
import tech.justdev.domain.revenue.entity.OwnershipShareTimeline
import tech.justdev.domain.revenue.valueobject.OwnershipPercentage
import tech.justdev.domain.revenue.valueobject.OwnershipShare
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.groupUuid
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.memberEmailString
import tech.justdev.testsupport.ownershipShareChangeId
import java.time.Instant
import java.time.LocalDate

class PreviewRevenueDistributionAtDateUseCaseTest {
    @Test
    fun `invoke should distribute revenue from the shares effective on the requested date`() {
        runTest {
            val useCase =
                PreviewRevenueDistributionAtDateUseCase(
                    groupAccessPolicy = GroupAccessPolicy(InMemoryGroupRepository(listOf(group()))),
                    ownershipShareTimelineRepository =
                        InMemoryOwnershipShareTimelineRepository(
                            timelines =
                                listOf(
                                    OwnershipShareTimeline(
                                        group = groupId("group-1"),
                                        changes =
                                            listOf(
                                                change(
                                                    id = "change-1",
                                                    effectiveDate = LocalDate.parse("2026-01-01"),
                                                    shares =
                                                        setOf(
                                                            share("alice", 6000),
                                                            share("bob", 4000),
                                                        ),
                                                ),
                                                change(
                                                    id = "change-2",
                                                    effectiveDate = LocalDate.parse("2026-03-01"),
                                                    shares =
                                                        setOf(
                                                            share("alice", 5000),
                                                            share("bob", 3000),
                                                            share("carol", 2000),
                                                        ),
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                )

            val preview =
                useCase(
                    PreviewRevenueDistributionAtDateQuery(
                        group = groupId("group-1"),
                        requestedBy = memberEmail("owner"),
                        amount = MoneyAmount.ofCents(101),
                        effectiveDate = LocalDate.parse("2026-03-15"),
                    ),
                )

            assertEquals(
                RevenueDistributionAtDatePreview(
                    group = groupUuid("group-1"),
                    effectiveDate = LocalDate.parse("2026-03-15"),
                    totalAmountInCents = 101,
                    allocations =
                        listOf(
                            PreviewRevenueDistributionAllocation(memberEmailString("alice"), 51),
                            PreviewRevenueDistributionAllocation(memberEmailString("bob"), 30),
                            PreviewRevenueDistributionAllocation(memberEmailString("carol"), 20),
                        ),
                ),
                preview,
            )
        }
    }

    private fun share(
        member: String,
        basisPoints: Int,
    ) = OwnershipShare(
        member = memberEmail(member),
        percentage = OwnershipPercentage.ofBasisPoints(basisPoints),
    )

    private fun change(
        id: String,
        effectiveDate: LocalDate,
        shares: Set<OwnershipShare>,
    ) = OwnershipShareChange(
        id = ownershipShareChangeId(id),
        effectiveDate = effectiveDate,
        recordedBy = memberEmail("owner"),
        recordedAt = Instant.parse("2026-04-03T10:00:00Z"),
        shares = shares,
    )

    private fun group(): Group =
        Group.create(
            id = groupId("group-1"),
            createdBy = memberEmail("owner"),
            createdAt = Instant.parse("2026-01-01T08:00:00Z"),
        )
}
