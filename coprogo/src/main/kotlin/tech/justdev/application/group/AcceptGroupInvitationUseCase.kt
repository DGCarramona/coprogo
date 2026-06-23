package tech.justdev.application.group

import jakarta.inject.Singleton
import tech.justdev.application.shared.TransactionRunner
import tech.justdev.domain.group.entity.GroupInvitationId
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.GroupInvitationRepository
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import java.time.Instant
import java.util.UUID

data class AcceptGroupInvitationCommand(
    val invitation: UUID,
    val acceptedBy: MemberEmail,
    val acceptedAt: Instant,
)

@Singleton
open class AcceptGroupInvitationUseCase(
    private val memberRepository: MemberRepository,
    private val groupRepository: GroupRepository,
    private val groupInvitationRepository: GroupInvitationRepository,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(command: AcceptGroupInvitationCommand) =
        transactionRunner.transaction {
            accept(command)
        }

    private suspend fun accept(command: AcceptGroupInvitationCommand) {
        val invitationId = GroupInvitationId(command.invitation)
        val invitation = groupInvitationRepository.findById(invitationId) ?: throw GroupInvitationNotFoundException(invitationId)

        if (!invitation.isPending()) {
            throw GroupInvitationAlreadyAcceptedException(invitationId)
        }
        if (invitation.invitedMember != command.acceptedBy) {
            throw GroupInvitationAccessDeniedException(invitationId, command.acceptedBy)
        }

        val group = groupRepository.findById(invitation.group) ?: throw GroupNotFoundException(invitation.group)

        if (memberRepository.findByEmail(command.acceptedBy) == null) {
            memberRepository.persist(
                Member(
                    email = command.acceptedBy,
                    createdAt = command.acceptedAt,
                ),
            )
        }

        val acceptedInvitation = invitation.accept(command.acceptedBy, command.acceptedAt)
        val updatedGroup = group.addMember(command.acceptedBy, command.acceptedAt)

        groupInvitationRepository.persist(acceptedInvitation)
        groupRepository.persist(updatedGroup)
    }
}
