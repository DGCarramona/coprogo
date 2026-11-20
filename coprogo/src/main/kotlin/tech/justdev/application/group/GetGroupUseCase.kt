package tech.justdev.application.group

import jakarta.inject.Singleton
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId
import java.time.Instant
import java.util.UUID

data class GetGroupQuery(
    val group: UUID,
    val requestedBy: MemberEmail,
)

data class GroupSnapshot(
    val group: UUID,
    val createdBy: String,
    val createdAt: Instant,
    val members: List<GroupMemberSnapshot>,
    val name: String? = null,
)

data class GroupMemberSnapshot(
    val member: String,
    val joinedAt: Instant,
    val addedBy: String?,
)

@Singleton
class GetGroupUseCase(
    private val groupAccessPolicy: GroupAccessPolicy,
) {
    suspend operator fun invoke(query: GetGroupQuery): GroupSnapshot {
        val groupId = GroupId(query.group)
        val group = groupAccessPolicy.requireMember(groupId, query.requestedBy)

        return GroupSnapshot(
            group = group.id.toPrimitive(),
            createdBy = group.createdBy.toPrimitive(),
            createdAt = group.createdAt,
            name = group.name?.toPrimitive(),
            members =
                group.members
                    .sortedBy { member -> member.member.toPrimitive() }
                    .map { member ->
                        GroupMemberSnapshot(
                            member = member.member.toPrimitive(),
                            joinedAt = member.joinedAt,
                            addedBy = member.addedBy?.toPrimitive(),
                        )
                    },
        )
    }
}
