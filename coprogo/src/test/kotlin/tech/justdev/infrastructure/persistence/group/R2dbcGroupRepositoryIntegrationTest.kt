package tech.justdev.infrastructure.persistence.group

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.testsupport.UsesPostgresTestDatabase
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import java.time.Instant

@MicronautTest(transactional = false)
@UsesPostgresTestDatabase
class R2dbcGroupRepositoryIntegrationTest {
    @Inject
    lateinit var groupRepository: GroupRepository

    @Inject
    lateinit var memberRepository: MemberRepository

    @Test
    fun `persist and findById should persist the group creator and memberships`() =
        runTest {
            memberRepository.persist(
                Member(
                    email = memberEmail("group-repo-alice"),
                    createdAt = Instant.parse("2026-04-13T10:00:00Z"),
                ),
            )
            memberRepository.persist(
                Member(
                    email = memberEmail("group-repo-bob"),
                    createdAt = Instant.parse("2026-04-13T10:05:00Z"),
                ),
            )

            val storedGroup =
                Group
                    .create(
                        id = groupId("group-repo-1"),
                        createdBy = memberEmail("group-repo-alice"),
                        createdAt = Instant.parse("2026-04-13T10:15:30Z"),
                    ).addMember(
                        member = memberEmail("group-repo-bob"),
                        joinedAt = Instant.parse("2026-04-14T08:00:00Z"),
                    )

            groupRepository.persist(storedGroup)

            assertEquals(storedGroup, groupRepository.findById(storedGroup.id))
        }

    @Test
    fun `persist should replace stored memberships with the current group state`() =
        runTest {
            memberRepository.persist(
                Member(
                    email = memberEmail("group-repo-owner"),
                    createdAt = Instant.parse("2026-04-13T11:00:00Z"),
                ),
            )
            memberRepository.persist(
                Member(
                    email = memberEmail("group-repo-carol"),
                    createdAt = Instant.parse("2026-04-13T11:05:00Z"),
                ),
            )

            val initialGroup =
                Group.create(
                    id = groupId("group-repo-2"),
                    createdBy = memberEmail("group-repo-owner"),
                    createdAt = Instant.parse("2026-04-13T11:15:30Z"),
                )
            val updatedGroup =
                initialGroup.addMember(
                    member = memberEmail("group-repo-carol"),
                    joinedAt = Instant.parse("2026-04-15T08:00:00Z"),
                )

            groupRepository.persist(initialGroup)
            groupRepository.persist(updatedGroup)

            assertEquals(updatedGroup, groupRepository.findById(updatedGroup.id))
        }
}
