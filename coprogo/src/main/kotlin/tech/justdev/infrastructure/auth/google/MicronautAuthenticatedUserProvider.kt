package tech.justdev.infrastructure.auth.google

import jakarta.inject.Singleton
import tech.justdev.application.auth.AuthenticatedEmailProvider
import tech.justdev.application.auth.AuthenticatedUser
import tech.justdev.application.auth.AuthenticatedUserProvider
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import java.time.Instant

@Singleton
class MicronautAuthenticatedUserProvider(
    private val authenticatedEmailProvider: AuthenticatedEmailProvider,
    private val memberRepository: MemberRepository,
) : AuthenticatedUserProvider {
    override suspend fun currentAuthenticatedUser(): AuthenticatedUser {
        val email = authenticatedEmailProvider.currentAuthenticatedEmail()
        val member = memberRepository.findByEmail(email) ?: autoRegisterMember(email)

        return AuthenticatedUser(email = member.email)
    }

    private suspend fun autoRegisterMember(email: MemberEmail): Member {
        memberRepository.persist(
            Member(
                email = email,
                createdAt = Instant.now(),
            ),
        )

        return memberRepository.findByEmail(email)
            ?: error("member ${email.toPrimitive()} should exist after automatic registration")
    }
}
