package tech.justdev.application.group

import jakarta.inject.Singleton
import tech.justdev.application.shared.TransactionRunner
import tech.justdev.domain.group.entity.GroupInvitationId
import tech.justdev.domain.group.repository.GroupInvitationRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId

data class CancelGroupInvitationCommand(
    val group: GroupId,
    val invitation: GroupInvitationId,
    val cancelledBy: MemberEmail,
)

interface CancelGroupInvitationUseCase {
    suspend operator fun invoke(command: CancelGroupInvitationCommand)
}

@Singleton
class CancelGroupInvitationUseCaseImpl(
    private val groupAccessPolicy: GroupAccessPolicy,
    private val invitations: GroupInvitationRepository,
    private val transactionRunner: TransactionRunner,
) : CancelGroupInvitationUseCase {
    override suspend fun invoke(command: CancelGroupInvitationCommand) {
        val group = groupAccessPolicy.requireMember(command.group, command.cancelledBy)
        transactionRunner.transaction {
            val invitation =
                invitations
                    .findById(command.invitation)
                    ?.takeIf { it.group == group.id }
                    ?: throw GroupInvitationNotFoundException(command.invitation)
            if (command.cancelledBy != invitation.invitedBy && command.cancelledBy != group.createdBy) {
                throw GroupInvitationCancellationDeniedException(command.invitation, command.cancelledBy)
            }
            if (!invitation.isPending()) {
                throw GroupInvitationAlreadyAcceptedException(command.invitation)
            }
            invitations.deletePending(invitation.id)
        }
    }
}
