package tech.justdev.domain.revenue.valueobject

import tech.justdev.domain.shared.valueobject.MemberId
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.money.sum

data class RevenueDistribution(
    val totalAmount: MoneyAmount,
    val allocations: Set<RevenueAllocation>,
) {
    init {
        require(allocations.isNotEmpty()) { "allocations must not be empty" }
        require(allocations.map { it.memberId }.toSet().size == allocations.size) { "allocations must contain unique members" }
        require(allocations.map { it.amount }.sum() == totalAmount) { "allocation amounts must add up to totalAmount" }
    }

    companion object {
        fun distribute(totalAmount: MoneyAmount, ownershipShares: Set<OwnershipShare>): RevenueDistribution {
            require(ownershipShares.isNotEmpty()) { "ownershipShares must not be empty" }
            require(ownershipShares.map { it.memberId }.toSet().size == ownershipShares.size) {
                "ownershipShares must contain unique members"
            }

            val totalOwnership = ownershipShares.sumOf { share -> share.percentage.inBasisPoints() }
            require(totalOwnership == OwnershipPercentage.ONE_HUNDRED_BASIS_POINTS) {
                "ownership shares must add up to 100.00"
            }

            val sortedShares = ownershipShares.sortedBy { share -> share.memberId.value }
            val flooredAllocations = sortedShares.map { share ->
                val rawAmountInBasisPoints = Math.multiplyExact(
                    totalAmount.inCents(),
                    share.percentage.inBasisPoints().toLong(),
                )
                FlooredRevenueAllocation(
                    memberId = share.memberId,
                    flooredAmount = MoneyAmount.ofCents(rawAmountInBasisPoints / OwnershipPercentage.ONE_HUNDRED_BASIS_POINTS),
                    remainder = rawAmountInBasisPoints % OwnershipPercentage.ONE_HUNDRED_BASIS_POINTS,
                )
            }

            val allocatedAmount = flooredAllocations.map { allocation -> allocation.flooredAmount }.sum()
            val remainingCentCount = (totalAmount - allocatedAmount).inCents().toInt()
            val memberIdsReceivingExtraCent = flooredAllocations
                .sortedWith(compareByDescending<FlooredRevenueAllocation> { allocation -> allocation.remainder }
                    .thenBy { allocation -> allocation.memberId.value })
                .take(remainingCentCount)
                .map { allocation -> allocation.memberId }
                .toSet()

            return RevenueDistribution(
                totalAmount = totalAmount,
                allocations = flooredAllocations
                    .map { allocation ->
                        RevenueAllocation(
                            memberId = allocation.memberId,
                            amount = allocation.flooredAmount +
                                if (allocation.memberId in memberIdsReceivingExtraCent) ONE_CENT else MoneyAmount.ZERO,
                        )
                    }
                    .toSet(),
            )
        }
    }
}

private data class FlooredRevenueAllocation(
    val memberId: MemberId,
    val flooredAmount: MoneyAmount,
    val remainder: Long,
)

private val ONE_CENT = MoneyAmount.ofCents(1)
