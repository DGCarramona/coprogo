package tech.justdev.infrastructure.persistence.group

import jakarta.inject.Singleton
import tech.justdev.domain.group.entity.GroupInvitation
import tech.justdev.domain.group.entity.GroupInvitationId
import tech.justdev.domain.group.repository.GroupInvitationRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId

@Singleton
class R2dbcGroupInvitationRepository(
    private val groupInvitationDataRepository: GroupInvitationDataRepository,
) : GroupInvitationRepository {
    override suspend fun findById(id: GroupInvitationId): GroupInvitation? =
        groupInvitationDataRepository.findById(id.toPrimitive())?.toDomain()

    override suspend fun findPendingByGroup(group: GroupId): List<GroupInvitation> =
        groupInvitationDataRepository
            .findPendingByGroup(group.toPrimitive())
            .map { invitation -> invitation.toDomain() }

    override suspend fun findPendingByInvitedMember(invitedMember: MemberEmail): List<GroupInvitation> =
        groupInvitationDataRepository
            .findPendingByInvitedEmail(invitedMember.toPrimitive())
            .map { invitation -> invitation.toDomain() }

    override suspend fun persist(invitation: GroupInvitation) {
        groupInvitationDataRepository.upsert(
            id = invitation.id.toPrimitive(),
            group = invitation.group.toPrimitive(),
            invitedEmail = invitation.invitedMember.toPrimitive(),
            invitedBy = invitation.invitedBy.toPrimitive(),
            invitedAt = invitation.invitedAt,
            acceptedBy = invitation.acceptedBy?.toPrimitive(),
            acceptedAt = invitation.acceptedAt,
        )
    }
}

private fun GroupInvitationEntity.toDomain(): GroupInvitation =
    GroupInvitation(
        id = GroupInvitationId(id),
        group = GroupId(group),
        invitedMember = MemberEmail.of(invitedEmail),
        invitedBy = MemberEmail.of(invitedBy),
        invitedAt = invitedAt,
        acceptedBy = acceptedBy?.let(MemberEmail::of),
        acceptedAt = acceptedAt,
    )
