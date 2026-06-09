package tech.justdev.domain.group.entity

import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId
import java.time.Instant
import java.util.UUID

@JvmInline
value class GroupInvitationId(
    private val value: UUID,
) {
    fun toPrimitive(): UUID = value
}

data class GroupInvitation(
    val id: GroupInvitationId,
    val group: GroupId,
    val invitedMember: MemberEmail,
    val invitedBy: MemberEmail,
    val invitedAt: Instant,
    val acceptedBy: MemberEmail? = null,
    val acceptedAt: Instant? = null,
) {
    init {
        require((acceptedBy == null) == (acceptedAt == null)) {
            "acceptedBy and acceptedAt must either both be null or both be set"
        }
    }

    fun isPending(): Boolean = acceptedAt == null

    fun accept(
        member: MemberEmail,
        acceptedAt: Instant,
    ): GroupInvitation {
        require(isPending()) { "group invitation is already accepted" }
        require(member == invitedMember) { "only the invited member can accept the invitation" }

        return copy(
            acceptedBy = member,
            acceptedAt = acceptedAt,
        )
    }
}
