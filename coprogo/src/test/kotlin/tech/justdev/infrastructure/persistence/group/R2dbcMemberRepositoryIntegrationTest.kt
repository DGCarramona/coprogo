package tech.justdev.infrastructure.persistence.group

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.testsupport.UsesPostgresTestDatabase
import java.time.Instant

@MicronautTest(transactional = false)
@UsesPostgresTestDatabase
class R2dbcMemberRepositoryIntegrationTest {
    @Inject
    lateinit var memberRepository: MemberRepository

    @Test
    fun `persist and findByEmail should persist a member`() =
        runTest {
            val member =
                Member(
                    email = MemberEmail.of("alice.member.repo@example.com"),
                    createdAt = Instant.parse("2026-04-13T10:15:30Z"),
                )

            memberRepository.persist(member)

            assertEquals(member, memberRepository.findByEmail(MemberEmail.of("ALICE.MEMBER.REPO@example.com")))
        }

    @Test
    fun `persist should upsert by canonical email without creating a second member row`() =
        runTest {
            val initialMember =
                Member(
                    email = MemberEmail.of("upsert.member.repo@example.com"),
                    createdAt = Instant.parse("2026-04-13T11:15:30Z"),
                )
            val conflictingMember =
                Member(
                    email = MemberEmail.of("UPSERT.MEMBER.REPO@example.com"),
                    createdAt = Instant.parse("2026-04-14T09:00:00Z"),
                )

            memberRepository.persist(initialMember)
            memberRepository.persist(conflictingMember)

            assertEquals(initialMember, memberRepository.findByEmail(initialMember.email))
        }
}
