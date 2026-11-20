package tech.justdev.infrastructure.persistence.group

import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Inject
import jakarta.inject.Named
import kotlinx.coroutines.test.runTest
import org.jooq.exception.IntegrityConstraintViolationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.domain.group.valueobject.GroupName
import tech.justdev.infrastructure.persistence.jooq.Tables.GROUP_MEMBERSHIPS
import tech.justdev.infrastructure.persistence.jooq.awaitList
import tech.justdev.infrastructure.persistence.jooq.dsl
import tech.justdev.testsupport.PostgresMicronautTest
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import java.time.Instant

@PostgresMicronautTest
class R2dbcGroupRepositoryIntegrationTest {
    @Inject
    @field:Named("default")
    lateinit var connectionFactory: ConnectionFactory

    @Inject
    lateinit var groupRepository: GroupRepository

    @Inject
    lateinit var memberRepository: MemberRepository

    @Nested
    inner class Persist {
        @Test
        fun `creates missing members and preserves existing member creation dates`() =
            runTest {
                val owner = memberEmail("auto-create-group-owner")
                val existing = memberEmail("auto-create-group-existing")
                val added = memberEmail("auto-create-group-added")
                val createdAt = Instant.parse("2026-04-13T10:00:00Z")
                val joinedAt = createdAt.plusSeconds(60)
                val existingMember = Member(existing, createdAt.minusSeconds(60))
                memberRepository.persist(existingMember)
                val group =
                    Group
                        .create(groupId("auto-create-group"), owner, createdAt)
                        .addMember(existing, joinedAt, owner)
                        .addMember(added, joinedAt.plusSeconds(60), owner)

                groupRepository.persist(group)

                assertEquals(group, groupRepository.findById(group.id))
                assertEquals(Member(owner, createdAt), memberRepository.findByEmail(owner))
                assertEquals(existingMember, memberRepository.findByEmail(existing))
                assertEquals(Member(added, joinedAt.plusSeconds(60)), memberRepository.findByEmail(added))
            }

        @Test
        fun `rolls back newly created members when membership persistence fails`() {
            val owner = memberEmail("rollback-auto-create-owner")
            val added = memberEmail("rollback-auto-create-added")
            val group =
                Group
                    .create(groupId("rollback-auto-create"), owner, Instant.EPOCH)
                    .addMember(added, Instant.EPOCH, memberEmail("rollback-auto-create-unknown"))

            assertThrows<IntegrityConstraintViolationException> {
                runTest { groupRepository.persist(group) }
            }

            runTest {
                assertNull(groupRepository.findById(group.id))
                assertNull(memberRepository.findByEmail(owner))
                assertNull(memberRepository.findByEmail(added))
            }
        }

        @Test
        fun `renaming preserves existing membership rows and dates`() =
            runTest {
                val group = groupWithMember("rename-preserve-memberships")
                groupRepository.persist(group)
                val before = membershipRows(group)
                val renamed = group.rename(GroupName.of("Appartement familial"))

                groupRepository.persist(renamed)

                assertEquals(renamed, groupRepository.findById(group.id))
                assertEquals(before, membershipRows(group))
            }

        @Test
        fun `persisting an older group snapshot does not erase added members`() =
            runTest {
                val group = groupWithMember("preserve-concurrent-memberships")
                val olderSnapshot = group.copy(members = group.members.filter { it.member == group.createdBy }.toSet())
                groupRepository.persist(olderSnapshot)
                groupRepository.persist(group)
                val rowsBefore = membershipRows(group)

                groupRepository.persist(olderSnapshot.rename(GroupName.of("Nouveau nom")))

                assertEquals(group.rename(GroupName.of("Nouveau nom")), groupRepository.findById(group.id))
                assertEquals(rowsBefore, membershipRows(group))
            }

        @Test
        fun `should persist the group creator and memberships`() =
            runTest {
                val storedGroup = groupWithMember("persist-group-repo")

                groupRepository.persist(storedGroup)

                assertEquals(storedGroup, groupRepository.findById(storedGroup.id))
            }

        @Test
        fun `should replace stored memberships with the current group state`() =
            runTest {
                val owner = memberEmail("replace-group-repo-owner")
                val member = memberEmail("replace-group-repo-member")
                persistMember(owner)
                persistMember(member)

                val initialGroup =
                    Group.create(
                        id = groupId("replace-group-repo"),
                        createdBy = owner,
                        createdAt = Instant.parse("2026-04-13T11:15:30Z"),
                    )
                val updatedGroup =
                    initialGroup.addMember(
                        member = member,
                        joinedAt = Instant.parse("2026-04-15T08:00:00Z"),
                    )

                groupRepository.persist(initialGroup)
                groupRepository.persist(updatedGroup)

                assertEquals(updatedGroup, groupRepository.findById(updatedGroup.id))
            }
    }

    @Nested
    inner class FindById {
        @Test
        fun `should find a persisted group with memberships`() =
            runTest {
                val storedGroup = groupWithMember("find-group-repo")
                groupRepository.persist(storedGroup)

                assertEquals(storedGroup, groupRepository.findById(storedGroup.id))
            }

        @Test
        fun `should return null when no group exists for the id`() =
            runTest {
                assertEquals(null, groupRepository.findById(groupId("missing-group-repo")))
            }
    }

    private suspend fun groupWithMember(seed: String): Group {
        val owner = memberEmail("$seed-owner")
        val member = memberEmail("$seed-member")
        persistMember(owner)
        persistMember(member)

        return Group
            .create(
                id = groupId(seed),
                createdBy = owner,
                createdAt = Instant.parse("2026-04-13T10:15:30Z"),
            ).addMember(
                member = member,
                joinedAt = Instant.parse("2026-04-14T08:00:00Z"),
            )
    }

    private suspend fun membershipRows(group: Group) =
        connectionFactory
            .dsl()
            .select(GROUP_MEMBERSHIPS.ID, GROUP_MEMBERSHIPS.MEMBER_EMAIL, GROUP_MEMBERSHIPS.JOINED_AT)
            .from(GROUP_MEMBERSHIPS)
            .where(GROUP_MEMBERSHIPS.GROUP.eq(group.id.toPrimitive()))
            .orderBy(GROUP_MEMBERSHIPS.MEMBER_EMAIL)
            .awaitList()

    private suspend fun persistMember(email: tech.justdev.domain.group.valueobject.MemberEmail) {
        memberRepository.persist(
            Member(
                email = email,
                createdAt = Instant.parse("2026-04-13T10:00:00Z"),
            ),
        )
    }
}
