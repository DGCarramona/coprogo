package tech.justdev.infrastructure.auth.google

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.application.auth.AuthenticatedEmailProvider
import tech.justdev.application.support.InMemoryMemberRepository
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.testsupport.memberEmail
import java.time.Instant

class MicronautAuthenticatedUserProviderTest {
    @Nested
    inner class CurrentAuthenticatedUser {
        @Test
        fun `should return an existing member without replacing it`() =
            runTest {
                val email = memberEmail("existing")
                val repository =
                    InMemoryMemberRepository(
                        listOf(Member(email = email, createdAt = Instant.parse("2026-04-03T10:00:00Z"))),
                    )

                val authenticated = provider(email, repository).currentAuthenticatedUser()

                assertEquals(email, authenticated.email)
                assertEquals(Instant.parse("2026-04-03T10:00:00Z"), repository.findByEmail(email)?.createdAt)
            }

        @Test
        fun `should persist and return a previously unknown member`() =
            runTest {
                val email = memberEmail("new")
                val repository = InMemoryMemberRepository()

                val authenticated = provider(email, repository).currentAuthenticatedUser()

                assertEquals(email, authenticated.email)
                assertEquals(email, repository.findByEmail(email)?.email)
            }

        @Test
        fun `should fail when automatic registration cannot be read back`() {
            val email = memberEmail("missing-after-registration")
            val repository =
                object : MemberRepository {
                    override suspend fun findByEmail(email: MemberEmail): Member? = null

                    override suspend fun persist(member: Member) = Unit
                }

            val error =
                assertThrows<IllegalStateException> {
                    runTest { provider(email, repository).currentAuthenticatedUser() }
                }

            assertEquals(
                "member ${email.toPrimitive()} should exist after automatic registration",
                error.message,
            )
        }
    }

    private fun provider(
        email: MemberEmail,
        repository: MemberRepository,
    ): MicronautAuthenticatedUserProvider =
        MicronautAuthenticatedUserProvider(
            authenticatedEmailProvider =
                object : AuthenticatedEmailProvider {
                    override suspend fun currentAuthenticatedEmail(): MemberEmail = email
                },
            memberRepository = repository,
        )
}
