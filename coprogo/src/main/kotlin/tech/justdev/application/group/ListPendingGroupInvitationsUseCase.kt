package tech.justdev.application.group

import jakarta.inject.Singleton
import tech.justdev.domain.group.repository.GroupInvitationRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import java.time.Instant
import java.util.UUID

data class ListPendingGroupInvitationsQuery(
    val requestedBy: MemberEmail,
)

data class PendingGroupInvitationSnapshot(
    val invitation: UUID,
    val group: UUID,
    val invitedMember: String,
    val invitedBy: String,
    val invitedAt: Instant,
)

@Singleton
class ListPendingGroupInvitationsUseCase(
    private val groupInvitationRepository: GroupInvitationRepository,
) {
    suspend operator fun invoke(query: ListPendingGroupInvitationsQuery): List<PendingGroupInvitationSnapshot> =
        groupInvitationRepository
            .findPendingByInvitedMember(query.requestedBy)
            .sortedBy { invitation -> invitation.invitedAt }
            .map { invitation ->
                PendingGroupInvitationSnapshot(
                    invitation = invitation.id.toPrimitive(),
                    group = invitation.group.toPrimitive(),
                    invitedMember = invitation.invitedMember.toPrimitive(),
                    invitedBy = invitation.invitedBy.toPrimitive(),
                    invitedAt = invitation.invitedAt,
                )
            }
}
