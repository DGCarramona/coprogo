package tech.justdev.application.group

import jakarta.inject.Singleton
import tech.justdev.domain.group.repository.GroupInvitationRepository
import tech.justdev.domain.group.repository.GroupRepository
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
    val pendingInvitations: List<GroupInvitationSnapshot>,
)

data class GroupMemberSnapshot(
    val member: String,
    val joinedAt: Instant,
)

data class GroupInvitationSnapshot(
    val invitation: UUID,
    val invitedMember: String,
    val invitedBy: String,
    val invitedAt: Instant,
)

@Singleton
class GetGroupUseCase(
    private val groupRepository: GroupRepository,
    private val groupInvitationRepository: GroupInvitationRepository,
) {
    suspend operator fun invoke(query: GetGroupQuery): GroupSnapshot {
        val groupId = GroupId(query.group)
        val group = groupRepository.findById(groupId) ?: throw GroupNotFoundException(groupId)

        if (!group.contains(query.requestedBy)) {
            throw GroupAccessDeniedException(groupId, query.requestedBy)
        }

        return GroupSnapshot(
            group = group.id.toPrimitive(),
            createdBy = group.createdBy.toPrimitive(),
            createdAt = group.createdAt,
            members =
                group.members
                    .sortedBy { member -> member.member.toPrimitive() }
                    .map { member ->
                        GroupMemberSnapshot(
                            member = member.member.toPrimitive(),
                            joinedAt = member.joinedAt,
                        )
                    },
            pendingInvitations =
                groupInvitationRepository
                    .findPendingByGroup(group.id)
                    .sortedBy { invitation -> invitation.invitedMember.toPrimitive() }
                    .map { invitation ->
                        GroupInvitationSnapshot(
                            invitation = invitation.id.toPrimitive(),
                            invitedMember = invitation.invitedMember.toPrimitive(),
                            invitedBy = invitation.invitedBy.toPrimitive(),
                            invitedAt = invitation.invitedAt,
                        )
                    },
        )
    }
}
