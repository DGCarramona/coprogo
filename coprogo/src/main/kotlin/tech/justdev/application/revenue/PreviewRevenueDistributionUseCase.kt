package tech.justdev.application.revenue

import jakarta.inject.Singleton
import tech.justdev.domain.revenue.OwnershipShare
import tech.justdev.domain.revenue.RevenueDistribution
import tech.justdev.domain.revenue.RevenueDistributionService
import java.math.BigDecimal

@Singleton
class PreviewRevenueDistributionUseCase(
    private val revenueDistributionService: RevenueDistributionService,
) {

    operator fun invoke(command: PreviewRevenueDistributionCommand): RevenueDistribution {
        val shares = command.members
            .map { member ->
                OwnershipShare(
                    memberId = member.memberId,
                    percentage = member.percentage,
                )
            }
            .toSet()

        return revenueDistributionService.distribute(
            totalAmountInCents = command.amountInCents,
            ownershipShares = shares,
        )
    }
}

data class PreviewRevenueDistributionCommand(
    val amountInCents: Long,
    val members: Set<PreviewRevenueDistributionMember>,
)

data class PreviewRevenueDistributionMember(
    val memberId: String,
    val percentage: BigDecimal,
)
