package tech.justdev.domain.group.entity

import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId
import java.time.Instant

data class GroupMember(
    val member: MemberEmail,
    val joinedAt: Instant,
)

data class Group(
    val id: GroupId,
    val createdBy: MemberEmail,
    val createdAt: Instant,
    val members: Set<GroupMember>,
) {
    init {
        require(members.isNotEmpty()) { "group members must not be empty" }
        require(members.map { member -> member.member }.toSet().size == members.size) {
            "group members must contain unique members"
        }
        require(members.any { member -> member.member == createdBy }) {
            "group creator must be part of the group"
        }
    }

    fun contains(member: MemberEmail): Boolean = members.any { groupMember -> groupMember.member == member }

    fun addMember(
        member: MemberEmail,
        joinedAt: Instant,
    ): Group {
        require(!contains(member)) { "member is already part of the group" }

        return copy(members = members + GroupMember(member = member, joinedAt = joinedAt))
    }

    companion object {
        fun create(
            id: GroupId,
            createdBy: MemberEmail,
            createdAt: Instant,
        ): Group =
            Group(
                id = id,
                createdBy = createdBy,
                createdAt = createdAt,
                members = setOf(GroupMember(member = createdBy, joinedAt = createdAt)),
            )
    }
}
