package tech.justdev.application.group

import jakarta.inject.Singleton
import tech.justdev.domain.group.entity.GroupInvitation
import tech.justdev.domain.group.repository.GroupInvitationRepository
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId
import java.time.Instant
import java.util.UUID

data class InviteMemberToGroupCommand(
    val group: UUID,
    val invitedBy: MemberEmail,
    val invitedMember: MemberEmail,
    val invitedAt: Instant,
)

@Singleton
class InviteMemberToGroupUseCase(
    private val groupRepository: GroupRepository,
    private val groupInvitationRepository: GroupInvitationRepository,
    private val groupInvitationIdGenerator: GroupInvitationIdGenerator = RandomGroupInvitationIdGenerator,
) {
    suspend operator fun invoke(command: InviteMemberToGroupCommand) {
        val groupId = GroupId(command.group)
        val group = groupRepository.findById(groupId) ?: throw GroupNotFoundException(groupId)

        if (!group.contains(command.invitedBy)) {
            throw GroupAccessDeniedException(groupId, command.invitedBy)
        }
        require(!group.contains(command.invitedMember)) {
            "member ${command.invitedMember.toPrimitive()} is already part of group ${group.id.toPrimitive()}"
        }

        val hasPendingInvitation =
            groupInvitationRepository
                .findPendingByGroup(group.id)
                .any { invitation -> invitation.invitedMember == command.invitedMember }
        if (hasPendingInvitation) {
            throw GroupInvitationAlreadyExistsException(group.id, command.invitedMember)
        }

        groupInvitationRepository.persist(
            GroupInvitation(
                id = groupInvitationIdGenerator.next(),
                group = group.id,
                invitedMember = command.invitedMember,
                invitedBy = command.invitedBy,
                invitedAt = command.invitedAt,
            ),
        )
    }
}
