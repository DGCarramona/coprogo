package tech.justdev.domain.revenue.valueobject

import tech.justdev.domain.shared.valueobject.MemberId

data class OwnershipShare(
    val member: MemberId,
    val percentage: OwnershipPercentage,
)
