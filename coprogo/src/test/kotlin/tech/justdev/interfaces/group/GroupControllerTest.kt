package tech.justdev.interfaces.group

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tech.justdev.application.auth.AuthenticatedEmailProvider
import tech.justdev.application.auth.AuthenticatedUser
import tech.justdev.application.auth.AuthenticatedUserProvider
import tech.justdev.application.group.AcceptGroupInvitationUseCase
import tech.justdev.application.group.CreateGroupUseCase
import tech.justdev.application.group.GetGroupUseCase
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.application.group.InviteMemberToGroupUseCase
import tech.justdev.application.group.ListMemberGroupsQuery
import tech.justdev.application.group.ListMemberGroupsUseCase
import tech.justdev.application.group.ListPendingGroupInvitationsUseCase
import tech.justdev.application.group.MemberGroupSnapshot
import tech.justdev.application.shared.DirectTransactionRunner
import tech.justdev.application.support.InMemoryGroupInvitationRepository
import tech.justdev.application.support.InMemoryGroupRepository
import tech.justdev.application.support.InMemoryMemberRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.testsupport.groupId
import java.time.Instant

class GroupControllerTest {
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
                                    MemberGroupSnapshot(group, "bob@example.com", Instant.EPOCH),
                                    MemberGroupSnapshot(joinedGroup, "carol@example.com", Instant.EPOCH.plusSeconds(1)),
                                )
                            }
                        },
                    )

                assertEquals(
                    listOf(
                        MemberGroupResponse(group, "bob@example.com", Instant.EPOCH),
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
    listMemberGroupsUseCase: ListMemberGroupsUseCase,
): GroupController {
    val members = InMemoryMemberRepository()
    val groups = InMemoryGroupRepository()
    val invitations = InMemoryGroupInvitationRepository()
    val accessPolicy = GroupAccessPolicy(groups)
    return GroupController(
        authenticatedEmailProvider =
            object : AuthenticatedEmailProvider {
                override suspend fun currentAuthenticatedEmail() = member
            },
        authenticatedUserProvider =
            object : AuthenticatedUserProvider {
                override suspend fun currentAuthenticatedUser() = AuthenticatedUser(member)
            },
        createGroupUseCase = CreateGroupUseCase(members, groups),
        getGroupUseCase = GetGroupUseCase(accessPolicy, invitations),
        inviteMemberToGroupUseCase = InviteMemberToGroupUseCase(accessPolicy, invitations),
        acceptGroupInvitationUseCase = AcceptGroupInvitationUseCase(members, groups, invitations, DirectTransactionRunner),
        listPendingGroupInvitationsUseCase = ListPendingGroupInvitationsUseCase(invitations),
        listMemberGroupsUseCase = listMemberGroupsUseCase,
    )
}
