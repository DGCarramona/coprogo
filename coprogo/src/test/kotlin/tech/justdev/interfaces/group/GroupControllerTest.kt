package tech.justdev.interfaces.group

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tech.justdev.application.auth.AuthenticatedUser
import tech.justdev.application.auth.AuthenticatedUserProvider
import tech.justdev.application.group.AddMemberToGroupCommand
import tech.justdev.application.group.AddMemberToGroupUseCase
import tech.justdev.application.group.CreateGroupUseCase
import tech.justdev.application.group.GetGroupUseCase
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.application.group.ListMemberGroupsQuery
import tech.justdev.application.group.ListMemberGroupsUseCase
import tech.justdev.application.group.MemberGroupSnapshot
import tech.justdev.application.group.RenameGroupCommand
import tech.justdev.application.group.RenameGroupUseCase
import tech.justdev.application.support.InMemoryGroupRepository
import tech.justdev.application.support.InMemoryMemberRepository
import tech.justdev.domain.group.valueobject.GroupName
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.testsupport.groupId
import java.time.Instant

class GroupControllerTest {
    @Nested
    inner class Create {
        @Test
        fun `converts and persists the submitted name`() =
            runTest {
                val groups = InMemoryGroupRepository()
                val controller = controller(MemberEmail.of("alice@example.com"), groups = groups)

                val response = controller.create(CreateGroupRequest("  Appartement familial  "))

                val group = groups.findById(GroupId(requireNotNull(response.body()).group))
                assertEquals(GroupName.of("Appartement familial"), group?.name)
            }
    }

    @Nested
    inner class RenameGroup {
        @Test
        fun `passes the authenticated creator and validated name to the use case`() =
            runTest {
                val member = MemberEmail.of("alice@example.com")
                val group = groupId("controller-rename")
                val commands = mutableListOf<RenameGroupCommand>()
                val controller =
                    controller(
                        member,
                        renameGroupUseCase =
                            object : RenameGroupUseCase {
                                override suspend fun invoke(command: RenameGroupCommand) {
                                    commands.add(command)
                                }
                            },
                    )

                controller.renameGroup(group.toPrimitive(), RenameGroupRequest("  Maison  "))

                assertEquals(listOf(RenameGroupCommand(group, member, GroupName.of("Maison"))), commands)
            }
    }

    @Nested
    inner class AddMember {
        @Test
        fun `passes the authenticated member and validated email to the use case`() =
            runTest {
                val member = MemberEmail.of("alice@example.com")
                val group = groupId("controller-add-member")
                val commands = mutableListOf<AddMemberToGroupCommand>()
                val controller =
                    controller(
                        member,
                        addMemberToGroupUseCase =
                            object : AddMemberToGroupUseCase {
                                override suspend fun invoke(command: AddMemberToGroupCommand) {
                                    commands.add(command)
                                }
                            },
                    )

                controller.addMember(group.toPrimitive(), AddGroupMemberRequest("bob@example.com"))

                assertEquals(group, commands.single().group)
                assertEquals(member, commands.single().addedBy)
                assertEquals(MemberEmail.of("bob@example.com"), commands.single().member)
            }
    }

    @Nested
    inner class ListMemberGroups {
        @Test
        fun `uses the authenticated email and maps all summaries`() =
            runTest {
                val member = MemberEmail.of("alice@example.com")
                val group = groupId("controller-member-groups").toPrimitive()
                val joinedGroup = groupId("controller-joined-group").toPrimitive()
                val queries = mutableListOf<ListMemberGroupsQuery>()
                val controller =
                    controller(
                        member,
                        object : ListMemberGroupsUseCase {
                            override suspend fun invoke(query: ListMemberGroupsQuery): List<MemberGroupSnapshot> {
                                queries.add(query)
                                return listOf(
                                    MemberGroupSnapshot(group, "bob@example.com", Instant.EPOCH, "Appartement", true),
                                    MemberGroupSnapshot(joinedGroup, "carol@example.com", Instant.EPOCH.plusSeconds(1)),
                                )
                            }
                        },
                    )

                assertEquals(
                    listOf(
                        MemberGroupResponse(group, "bob@example.com", Instant.EPOCH, "Appartement", true),
                        MemberGroupResponse(joinedGroup, "carol@example.com", Instant.EPOCH.plusSeconds(1)),
                    ),
                    controller.listMemberGroups(),
                )
                assertEquals(listOf(ListMemberGroupsQuery(member)), queries)
            }

        @Test
        fun `returns no groups when the member has no memberships`() =
            runTest {
                val controller =
                    controller(
                        MemberEmail.of("new@example.com"),
                        object : ListMemberGroupsUseCase {
                            override suspend fun invoke(query: ListMemberGroupsQuery): List<MemberGroupSnapshot> = emptyList()
                        },
                    )

                assertEquals(emptyList<MemberGroupResponse>(), controller.listMemberGroups())
            }
    }
}

private fun controller(
    member: MemberEmail,
    listMemberGroupsUseCase: ListMemberGroupsUseCase =
        object : ListMemberGroupsUseCase {
            override suspend fun invoke(query: ListMemberGroupsQuery): List<MemberGroupSnapshot> = emptyList()
        },
    groups: InMemoryGroupRepository = InMemoryGroupRepository(),
    addMemberToGroupUseCase: AddMemberToGroupUseCase =
        object : AddMemberToGroupUseCase {
            override suspend fun invoke(command: AddMemberToGroupCommand) = Unit
        },
    renameGroupUseCase: RenameGroupUseCase =
        object : RenameGroupUseCase {
            override suspend fun invoke(command: RenameGroupCommand) = Unit
        },
): GroupController {
    val members = InMemoryMemberRepository()
    val accessPolicy = GroupAccessPolicy(groups)
    return GroupController(
        authenticatedUserProvider =
            object : AuthenticatedUserProvider {
                override suspend fun currentAuthenticatedUser() = AuthenticatedUser(member)
            },
        createGroupUseCase = CreateGroupUseCase(members, groups),
        getGroupUseCase = GetGroupUseCase(accessPolicy),
        addMemberToGroupUseCase = addMemberToGroupUseCase,
        listMemberGroupsUseCase = listMemberGroupsUseCase,
        renameGroupUseCase = renameGroupUseCase,
    )
}
