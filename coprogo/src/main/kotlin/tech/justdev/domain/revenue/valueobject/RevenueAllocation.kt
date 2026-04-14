package tech.justdev.domain.revenue.valueobject

import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.money.MoneyAmount

data class RevenueAllocation(
    val member: MemberEmail,
    val amount: MoneyAmount,
)
