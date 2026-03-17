package tech.justdev.domain.revenue

import jakarta.inject.Singleton
import java.math.BigDecimal
import java.math.RoundingMode

@Singleton
class RevenueDistributionService {

    fun distribute(totalAmountInCents: Long, ownershipShares: Set<OwnershipShare>): RevenueDistribution {
        require(totalAmountInCents >= 0) { "totalAmountInCents must be >= 0" }
        require(ownershipShares.isNotEmpty()) { "ownershipShares must not be empty" }

        val totalPercentage = ownershipShares
            .map { it.normalizedPercentage() }
            .reduce(BigDecimal::add)

        require(totalPercentage.compareTo(BigDecimal("100.00")) == 0) {
            "ownership shares must add up to 100.00"
        }

        val rawAllocations = ownershipShares.associate { share ->
            val raw = BigDecimal.valueOf(totalAmountInCents)
                .multiply(share.normalizedPercentage())
                .divide(BigDecimal("100"), 6, RoundingMode.HALF_UP)
            share.memberId to raw
        }

        val floored = rawAllocations.mapValues { (_, value) -> value.setScale(0, RoundingMode.DOWN).longValueExact() }
        val allocated = floored.values.sum()
        val remainder = (totalAmountInCents - allocated).toInt()

        val membersByRemainder = rawAllocations.entries
            .sortedByDescending { (_, raw) -> raw.remainder(BigDecimal.ONE) }
            .map { it.key }

        val finalAllocations = membersByRemainder.withIndex().fold(floored) { acc, indexedMember ->
            if (indexedMember.index >= remainder) {
                acc
            } else {
                acc + (indexedMember.value to (acc.getValue(indexedMember.value) + 1L))
            }
        }

        return RevenueDistribution(
            totalAmountInCents = totalAmountInCents,
            allocationsInCents = finalAllocations,
        )
    }
}
