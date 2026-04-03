package tech.justdev.domain.revenue.valueobject

import java.math.BigDecimal
import java.math.RoundingMode

@JvmInline
value class OwnershipPercentage private constructor(private val basisPoints: Int) {
    init {
        require(basisPoints in 1..ONE_HUNDRED_BASIS_POINTS) { "basisPoints must be between 1 and 10000" }
    }

    fun inBasisPoints(): Int = basisPoints

    companion object {
        const val ONE_HUNDRED_BASIS_POINTS = 10_000

        fun ofBasisPoints(basisPoints: Int): OwnershipPercentage = OwnershipPercentage(basisPoints)

        fun ofPercentage(percentage: BigDecimal): OwnershipPercentage {
            require(percentage.scale() <= 2) { "percentage supports at most 2 decimals" }
            val normalized = percentage.setScale(2, RoundingMode.UNNECESSARY)
            return ofBasisPoints(normalized.movePointRight(2).intValueExact())
        }
    }
}
