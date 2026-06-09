package tech.justdev.domain.group.repository

import tech.justdev.domain.group.entity.GroupInvitation
import tech.justdev.domain.group.entity.GroupInvitationId
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId

interface GroupInvitationRepository {
    suspend fun findById(id: GroupInvitationId): GroupInvitation?

    suspend fun findPendingByGroup(group: GroupId): List<GroupInvitation>

    suspend fun findPendingByInvitedMember(invitedMember: MemberEmail): List<GroupInvitation>

    suspend fun persist(invitation: GroupInvitation)
}
