package tech.justdev.domain.revenue

import java.math.BigDecimal
import java.math.RoundingMode

private val HUNDRED = BigDecimal("100")

data class OwnershipShare(
    val memberId: String,
    val percentage: BigDecimal,
) {
    init {
        require(memberId.isNotBlank()) { "memberId must not be blank" }
        require(percentage > BigDecimal.ZERO) { "percentage must be strictly positive" }
        require(percentage <= HUNDRED) { "percentage must be <= 100" }
        require(percentage.scale() <= 2) { "percentage supports at most 2 decimals" }
    }

    fun normalizedPercentage(): BigDecimal = percentage.setScale(2, RoundingMode.UNNECESSARY)
}
