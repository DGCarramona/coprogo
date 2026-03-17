package tech.justdev.domain.revenue

data class RevenueDistribution(
    val totalAmountInCents: Long,
    val allocationsInCents: Map<String, Long>,
)
