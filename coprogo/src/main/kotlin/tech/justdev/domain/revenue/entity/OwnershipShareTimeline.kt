package tech.justdev.domain.revenue.entity

import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.revenue.valueobject.OwnershipPercentage
import tech.justdev.domain.revenue.valueobject.OwnershipShare
import tech.justdev.domain.shared.valueobject.GroupId
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@JvmInline
value class OwnershipShareChangeId(
    private val value: UUID,
) {
    fun toPrimitive(): UUID = value
}

data class OwnershipShareChange(
    val id: OwnershipShareChangeId,
    val effectiveDate: LocalDate,
    val recordedBy: MemberEmail,
    val recordedAt: Instant,
    val shares: Set<OwnershipShare>,
) {
    init {
        require(shares.isNotEmpty()) { "shares must not be empty" }
        require(shares.map { it.member }.toSet().size == shares.size) {
            "shares must contain unique members"
        }
        require(shares.sumOf { share -> share.percentage.inBasisPoints() } == OwnershipPercentage.ONE_HUNDRED_BASIS_POINTS) {
            "ownership shares must add up to 100.00"
        }
    }
}

data class OwnershipShareTimeline(
    val group: GroupId,
    val changes: List<OwnershipShareChange>,
) {
    init {
        require(changes.map { change -> change.id }.toSet().size == changes.size) {
            "changes must contain unique ids"
        }
        require(changes.map { change -> change.effectiveDate }.toSet().size == changes.size) {
            "changes must contain unique effective dates"
        }
    }

    fun recordChange(change: OwnershipShareChange): OwnershipShareTimeline {
        require(change.id !in changes.map { existingChange -> existingChange.id }.toSet()) {
            "ownership share change id already exists"
        }
        require(change.effectiveDate !in changes.map { existingChange -> existingChange.effectiveDate }.toSet()) {
            "ownership share change effectiveDate already exists"
        }

        return copy(changes = changes + change)
    }

    fun sharesAt(date: LocalDate): Set<OwnershipShare> =
        changes
            .asSequence()
            .filter { change -> change.effectiveDate <= date }
            .maxByOrNull { change -> change.effectiveDate }
            ?.shares
            ?: throw IllegalArgumentException("no ownership shares are effective on $date")

    fun history(): List<OwnershipShareChange> = changes.sortedBy { change -> change.effectiveDate }

    companion object {
        fun empty(group: GroupId): OwnershipShareTimeline = OwnershipShareTimeline(group = group, changes = emptyList())
    }
}
