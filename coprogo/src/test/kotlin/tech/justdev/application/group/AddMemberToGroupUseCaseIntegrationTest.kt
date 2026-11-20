package tech.justdev.application.group

import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.application.shared.TransactionRunner
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.testsupport.PostgresMicronautTest
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import java.time.Instant

@PostgresMicronautTest
class AddMemberToGroupUseCaseIntegrationTest {
    @Inject
    lateinit var groups: GroupRepository

    @Inject
    lateinit var members: MemberRepository

    @Inject
    lateinit var transactionRunner: TransactionRunner

    @Test
    fun `adds a member and persists membership attribution`() {
        val now = Instant.parse("2026-04-14T10:00:00Z")
        val owner = memberEmail("integration-owner")
        val added = memberEmail("integration-added")
        val group = Group.create(groupId("add-member-integration"), owner, now)
        val addedAt = now.plusSeconds(60)
        runTest {
            groups.persist(group)
        }
        val useCase = AddMemberToGroupUseCaseImpl(GroupAccessPolicy(groups), groups, transactionRunner)

        runTest {
            useCase(AddMemberToGroupCommand(groupId("add-member-integration"), owner, added, addedAt))

            assertEquals(
                added,
                groups
                    .findById(group.id)!!
                    .members
                    .single { it.member == added }
                    .member,
            )
            assertEquals(
                owner,
                groups
                    .findById(group.id)!!
                    .members
                    .single { it.member == added }
                    .addedBy,
            )
            assertEquals(
                addedAt,
                groups
                    .findById(group.id)!!
                    .members
                    .single { it.member == added }
                    .joinedAt,
            )
            assertEquals(Member(added, addedAt), members.findByEmail(added))
        }
    }

    @Test
    fun `unauthorized addition does not create a member`() {
        val group = Group.create(groupId("add-member-denied-integration"), memberEmail("denied-owner"), Instant.EPOCH)
        val added = memberEmail("denied-added")
        runTest { groups.persist(group) }
        val useCase = AddMemberToGroupUseCaseImpl(GroupAccessPolicy(groups), groups, transactionRunner)

        assertThrows<GroupAccessDeniedException> {
            runTest {
                useCase(AddMemberToGroupCommand(group.id, memberEmail("not-a-member"), added, Instant.EPOCH))
            }
        }

        runTest { assertNull(members.findByEmail(added)) }
    }

    @Test
    fun `adding an existing member preserves their original creation date`() =
        runTest {
            val createdAt = Instant.parse("2026-01-01T08:00:00Z")
            val existing = Member(memberEmail("add-existing-member"), createdAt)
            val group = Group.create(groupId("add-existing-member-group"), memberEmail("add-existing-owner"), createdAt)
            members.persist(existing)
            groups.persist(group)
            val useCase = AddMemberToGroupUseCaseImpl(GroupAccessPolicy(groups), groups, transactionRunner)

            useCase(AddMemberToGroupCommand(group.id, group.createdBy, existing.email, createdAt.plusSeconds(60)))

            assertEquals(existing, members.findByEmail(existing.email))
            assertEquals(group.addMember(existing.email, createdAt.plusSeconds(60), group.createdBy), groups.findById(group.id))
        }
}
