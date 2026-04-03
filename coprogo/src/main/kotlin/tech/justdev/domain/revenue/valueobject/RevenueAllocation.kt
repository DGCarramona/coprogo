package tech.justdev.domain.revenue.valueobject

import tech.justdev.domain.shared.valueobject.MemberId
import tech.justdev.domain.shared.money.MoneyAmount

data class RevenueAllocation(
    val member: MemberId,
    val amount: MoneyAmount,
)
