package tech.justdev.application.group

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.application.support.InMemoryGroupRepository
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.valueobject.GroupName
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import java.time.Instant

class RenameGroupUseCaseTest {
    private val owner = memberEmail("rename-owner")
    private val member = memberEmail("rename-member")
    private val group = Group.create(groupId("rename-use-case"), owner, Instant.EPOCH).addMember(member, Instant.EPOCH)
    private val repository = InMemoryGroupRepository(listOf(group))
    private val useCase = RenameGroupUseCaseImpl(GroupAccessPolicy(repository), repository)
    private val name = GroupName.of("Appartement familial")

    @Nested
    inner class Invoke {
        @Test
        fun `lets the creator assign and later change the group name`() =
            runTest {
                useCase(RenameGroupCommand(group.id, owner, name))
                assertEquals(group.copy(name = name), repository.findById(group.id))

                val renamed = GroupName.of("Maison familiale")
                useCase(RenameGroupCommand(group.id, owner, renamed))
                assertEquals(group.copy(name = renamed), repository.findById(group.id))
            }

        @Test
        fun `rejects another member without changing the group`() {
            assertThrows<GroupCreatorRequiredException> {
                runTest {
                    useCase(RenameGroupCommand(group.id, member, name))
                }
            }
            runTest { assertEquals(group, repository.findById(group.id)) }
        }

        @Test
        fun `rejects an outsider before changing anything`() {
            assertThrows<GroupAccessDeniedException> {
                runTest {
                    useCase(RenameGroupCommand(group.id, memberEmail("outsider"), name))
                }
            }
            runTest { assertEquals(group, repository.findById(group.id)) }
        }

        @Test
        fun `rejects a missing group`() {
            assertThrows<GroupNotFoundException> {
                runTest {
                    useCase(RenameGroupCommand(groupId("missing-rename"), owner, name))
                }
            }
        }
    }
}
