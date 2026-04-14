package tech.justdev.domain.revenue.valueobject

import tech.justdev.domain.group.valueobject.MemberEmail

data class OwnershipShare(
    val member: MemberEmail,
    val percentage: OwnershipPercentage,
)
