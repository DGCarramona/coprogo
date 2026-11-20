package tech.justdev.application.group

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.application.shared.DirectTransactionRunner
import tech.justdev.application.shared.TransactionRunner
import tech.justdev.application.support.InMemoryGroupRepository
import tech.justdev.domain.group.entity.Group
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import java.time.Instant

class AddMemberToGroupUseCaseTest {
    @Test
    fun `completes successfully after a transaction suspends`() =
        runTest {
            val groups = InMemoryGroupRepository(listOf(Group.create(groupId("suspended"), memberEmail("alice"), Instant.EPOCH)))
            val transactionRunner =
                object : TransactionRunner {
                    override suspend fun <T> transaction(block: suspend () -> T): T {
                        yield()
                        return block()
                    }
                }
            val useCase = AddMemberToGroupUseCaseImpl(GroupAccessPolicy(groups), groups, transactionRunner)

            useCase(AddMemberToGroupCommand(groupId("suspended"), memberEmail("alice"), memberEmail("bob"), Instant.EPOCH))

            assertTrue(groups.findById(groupId("suspended"))!!.contains(memberEmail("bob")))
        }

    @Test
    fun `adds the member immediately and records who added them`() =
        runTest {
            val addedAt = Instant.parse("2026-04-14T10:00:00Z")
            val groups = InMemoryGroupRepository(listOf(Group.create(groupId("group-1"), memberEmail("alice"), addedAt)))
            val useCase = AddMemberToGroupUseCaseImpl(GroupAccessPolicy(groups), groups, DirectTransactionRunner)

            useCase(
                AddMemberToGroupCommand(
                    group = groupId("group-1"),
                    addedBy = memberEmail("alice"),
                    member = memberEmail("bob"),
                    addedAt = addedAt.plusSeconds(60),
                ),
            )

            val joinedMember = groups.findById(groupId("group-1"))!!.members.single { it.member == memberEmail("bob") }
            assertEquals(addedAt.plusSeconds(60), joinedMember.joinedAt)
            assertEquals(memberEmail("alice"), joinedMember.addedBy)
        }

    @Test
    fun `does not add a member when the requester is not in the group`() {
        val groups = InMemoryGroupRepository(listOf(Group.create(groupId("group-1"), memberEmail("alice"), Instant.EPOCH)))
        val useCase = AddMemberToGroupUseCaseImpl(GroupAccessPolicy(groups), groups, DirectTransactionRunner)

        assertThrows<GroupAccessDeniedException> {
            runTest {
                useCase(
                    AddMemberToGroupCommand(
                        group = groupId("group-1"),
                        addedBy = memberEmail("bob"),
                        member = memberEmail("carol"),
                        addedAt = Instant.EPOCH.plusSeconds(60),
                    ),
                )
            }
        }

        runTest {
            assertFalse(groups.findById(groupId("group-1"))!!.contains(memberEmail("carol")))
        }
    }

    @Test
    fun `does not change the original membership when the member already belongs to the group`() {
        val existingJoin = Instant.parse("2026-04-14T09:00:00Z")
        val originalGroup =
            Group
                .create(groupId("group-1"), memberEmail("alice"), Instant.EPOCH)
                .addMember(memberEmail("bob"), existingJoin, memberEmail("carol"))
        val groups = InMemoryGroupRepository(listOf(originalGroup))
        val useCase = AddMemberToGroupUseCaseImpl(GroupAccessPolicy(groups), groups, DirectTransactionRunner)

        assertThrows<IllegalArgumentException> {
            runTest {
                useCase(
                    AddMemberToGroupCommand(
                        group = groupId("group-1"),
                        addedBy = memberEmail("alice"),
                        member = memberEmail("bob"),
                        addedAt = existingJoin.plusSeconds(60),
                    ),
                )
            }
        }

        runTest { assertEquals(originalGroup, groups.findById(originalGroup.id)) }
    }
}
