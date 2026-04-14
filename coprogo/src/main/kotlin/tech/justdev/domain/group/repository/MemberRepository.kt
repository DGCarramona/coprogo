package tech.justdev.domain.group.repository

import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.valueobject.MemberEmail

interface MemberRepository {
    suspend fun findByEmail(email: MemberEmail): Member?

    suspend fun persist(member: Member)
}
