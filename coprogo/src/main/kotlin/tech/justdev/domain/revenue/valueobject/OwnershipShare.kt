package tech.justdev.domain.revenue.valueobject

import tech.justdev.domain.shared.valueobject.MemberId

data class OwnershipShare(
    val memberId: MemberId,
    val percentage: OwnershipPercentage,
) {
    init {
        require(memberId.value.isNotBlank()) { "memberId must not be blank" }
    }
}
