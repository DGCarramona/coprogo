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
        require(allocations.map { it.member }.toSet().size == allocations.size) { "allocations must contain unique members" }
        require(allocations.map { it.amount }.sum() == totalAmount) { "allocation amounts must add up to totalAmount" }
    }

    companion object {
        fun distribute(totalAmount: MoneyAmount, ownershipShares: Set<OwnershipShare>): RevenueDistribution {
            require(ownershipShares.isNotEmpty()) { "ownershipShares must not be empty" }
            require(ownershipShares.map { it.member }.toSet().size == ownershipShares.size) {
                "ownershipShares must contain unique members"
            }

            val totalOwnership = ownershipShares.sumOf { share -> share.percentage.inBasisPoints() }
            require(totalOwnership == OwnershipPercentage.ONE_HUNDRED_BASIS_POINTS) {
                "ownership shares must add up to 100.00"
            }

            val sortedShares = ownershipShares.sortedBy { share -> share.member.toPrimitive() }
            val flooredAllocations = sortedShares.map { share ->
                val rawAmountInBasisPoints = Math.multiplyExact(
                    totalAmount.inCents(),
                    share.percentage.inBasisPoints().toLong(),
                )
                FlooredRevenueAllocation(
                    member = share.member,
                    flooredAmount = MoneyAmount.ofCents(rawAmountInBasisPoints / OwnershipPercentage.ONE_HUNDRED_BASIS_POINTS),
                    remainder = rawAmountInBasisPoints % OwnershipPercentage.ONE_HUNDRED_BASIS_POINTS,
                )
            }

            val allocatedAmount = flooredAllocations.map { allocation -> allocation.flooredAmount }.sum()
            val remainingCentCount = (totalAmount - allocatedAmount).inCents().toInt()
            val membersReceivingExtraCent = flooredAllocations
                .sortedWith(compareByDescending<FlooredRevenueAllocation> { allocation -> allocation.remainder }
                    .thenBy { allocation -> allocation.member.toPrimitive() })
                .take(remainingCentCount)
                .map { allocation -> allocation.member }
                .toSet()

            return RevenueDistribution(
                totalAmount = totalAmount,
                allocations = flooredAllocations
                    .map { allocation ->
                        RevenueAllocation(
                            member = allocation.member,
                            amount = allocation.flooredAmount +
                                if (allocation.member in membersReceivingExtraCent) ONE_CENT else MoneyAmount.ZERO,
                        )
                    }
                    .toSet(),
            )
        }
    }
}

private data class FlooredRevenueAllocation(
    val member: MemberId,
    val flooredAmount: MoneyAmount,
    val remainder: Long,
)

private val ONE_CENT = MoneyAmount.ofCents(1)
