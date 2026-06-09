package tech.justdev.application.revenue

import jakarta.inject.Singleton
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.revenue.repository.OwnershipShareTimelineRepository
import tech.justdev.domain.revenue.valueobject.RevenueDistribution
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import java.time.LocalDate
import java.util.UUID

data class PreviewRevenueDistributionAtDateQuery(
    val group: UUID,
    val requestedBy: MemberEmail,
    val amountInCents: Long,
    val effectiveDate: LocalDate,
)

data class RevenueDistributionAtDatePreview(
    val group: UUID,
    val effectiveDate: LocalDate,
    val totalAmountInCents: Long,
    val allocations: List<PreviewRevenueDistributionAllocation>,
)

@Singleton
class PreviewRevenueDistributionAtDateUseCase(
    private val groupAccessPolicy: GroupAccessPolicy,
    private val ownershipShareTimelineRepository: OwnershipShareTimelineRepository,
) {
    suspend operator fun invoke(query: PreviewRevenueDistributionAtDateQuery): RevenueDistributionAtDatePreview {
        val group = GroupId(query.group)
        groupAccessPolicy.requireMember(group, query.requestedBy)

        val timeline =
            ownershipShareTimelineRepository.findByGroup(group)
                ?: throw IllegalArgumentException("ownership share timeline for group ${query.group} was not found")

        val distribution =
            RevenueDistribution.distribute(
                totalAmount = MoneyAmount.ofCents(query.amountInCents),
                ownershipShares = timeline.sharesAt(query.effectiveDate),
            )

        return RevenueDistributionAtDatePreview(
            group = group.toPrimitive(),
            effectiveDate = query.effectiveDate,
            totalAmountInCents = distribution.totalAmount.inCents(),
            allocations =
                distribution.allocations
                    .sortedBy { allocation -> allocation.member.toPrimitive() }
                    .map { allocation ->
                        PreviewRevenueDistributionAllocation(
                            member = allocation.member.toPrimitive(),
                            amountInCents = allocation.amount.inCents(),
                        )
                    },
        )
    }
}
