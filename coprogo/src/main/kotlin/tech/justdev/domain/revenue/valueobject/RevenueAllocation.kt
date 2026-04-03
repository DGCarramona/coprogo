package tech.justdev.domain.revenue.valueobject

import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.MemberId

data class RevenueAllocation(
    val member: MemberId,
    val amount: MoneyAmount,
)
