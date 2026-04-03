package tech.justdev.application.revenue

import jakarta.inject.Singleton
import tech.justdev.domain.revenue.valueobject.OwnershipPercentage
import tech.justdev.domain.revenue.valueobject.OwnershipShare
import tech.justdev.domain.revenue.valueobject.RevenueDistribution
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.MemberId
import java.math.BigDecimal
import java.util.UUID

@Singleton
class PreviewRevenueDistributionUseCase {
    operator fun invoke(command: PreviewRevenueDistributionCommand): PreviewRevenueDistributionResult {
        val shares =
            command.members
                .map { member ->
                    OwnershipShare(
                        member = MemberId(member.member),
                        percentage = OwnershipPercentage.ofPercentage(member.percentage),
                    )
                }.toSet()

        val distribution =
            RevenueDistribution.distribute(
                totalAmount = MoneyAmount.ofCents(command.amountInCents),
                ownershipShares = shares,
            )

        return PreviewRevenueDistributionResult(
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

data class PreviewRevenueDistributionCommand(
    val amountInCents: Long,
    val members: Set<PreviewRevenueDistributionMember>,
)

data class PreviewRevenueDistributionMember(
    val member: UUID,
    val percentage: BigDecimal,
)

data class PreviewRevenueDistributionResult(
    val totalAmountInCents: Long,
    val allocations: List<PreviewRevenueDistributionAllocation>,
)

data class PreviewRevenueDistributionAllocation(
    val member: UUID,
    val amountInCents: Long,
)
