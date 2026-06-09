package tech.justdev.infrastructure.persistence.group

import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.testsupport.PostgresMicronautTest
import java.time.Instant

@PostgresMicronautTest
class R2dbcMemberRepositoryIntegrationTest {
    @Inject
    lateinit var memberRepository: MemberRepository

    @Nested
    inner class Persist {
        @Test
        fun `should persist a member`() =
            runTest {
                val member = member("persist-member-repo")

                memberRepository.persist(member)

                assertEquals(member, memberRepository.findByEmail(member.email))
            }

        @Test
        fun `should upsert by canonical email without creating a second member row`() =
            runTest {
                val initialMember = member("upsert-member-repo")
                val conflictingMember =
                    Member(
                        email = MemberEmail.of("UPSERT-MEMBER-REPO@example.com"),
                        createdAt = Instant.parse("2026-04-14T09:00:00Z"),
                    )

                memberRepository.persist(initialMember)
                memberRepository.persist(conflictingMember)

                assertEquals(initialMember, memberRepository.findByEmail(initialMember.email))
            }
    }

    @Nested
    inner class FindByEmail {
        @Test
        fun `should find a member by normalized email`() =
            runTest {
                val member = member("find-member-repo")
                memberRepository.persist(member)

                assertEquals(member, memberRepository.findByEmail(MemberEmail.of("FIND-MEMBER-REPO@example.com")))
            }

        @Test
        fun `should return null when no member exists for the email`() =
            runTest {
                assertEquals(null, memberRepository.findByEmail(MemberEmail.of("missing-member-repo@example.com")))
            }
    }

    private fun member(seed: String): Member =
        Member(
            email = MemberEmail.of("$seed@example.com"),
            createdAt = Instant.parse("2026-04-13T10:15:30Z"),
        )
}
