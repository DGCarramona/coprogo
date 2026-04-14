package tech.justdev.infrastructure.persistence.group

import jakarta.inject.Singleton
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.domain.group.valueobject.MemberEmail

@Singleton
class R2dbcMemberRepository(
    private val memberDataRepository: MemberDataRepository,
) : MemberRepository {
    override suspend fun findByEmail(email: MemberEmail): Member? = memberDataRepository.findByEmail(email.toPrimitive())?.toDomain()

    override suspend fun persist(member: Member) {
        memberDataRepository.upsertByEmail(
            email = member.email.toPrimitive(),
            createdAt = member.createdAt,
        )
    }
}

private fun MemberEntity.toDomain(): Member =
    Member(
        email = MemberEmail.of(email),
        createdAt = createdAt,
    )
