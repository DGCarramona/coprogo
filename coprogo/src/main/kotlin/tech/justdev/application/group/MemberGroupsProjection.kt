package tech.justdev.application.group

import tech.justdev.domain.group.valueobject.MemberEmail

interface MemberGroupsProjection {
    suspend fun findByMember(member: MemberEmail): List<MemberGroupSnapshot>
}
