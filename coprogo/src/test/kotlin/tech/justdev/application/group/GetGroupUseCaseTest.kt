package tech.justdev.application.group

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.application.support.InMemoryGroupInvitationRepository
import tech.justdev.application.support.InMemoryGroupRepository
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.GroupInvitation
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.groupInvitationId
import tech.justdev.testsupport.groupInvitationUuid
import tech.justdev.testsupport.groupUuid
import tech.justdev.testsupport.memberEmail
import java.time.Instant

class GetGroupUseCaseTest {
    @Test
    fun `invoke should return members and pending invitations for a group member`() {
        runTest {
            val group =
                Group
                    .create(
                        id = groupId("group-1"),
                        createdBy = memberEmail("alice"),
                        createdAt = Instant.parse("2026-04-14T08:30:00Z"),
                    ).addMember(
                        member = memberEmail("bob"),
                        joinedAt = Instant.parse("2026-04-14T09:00:00Z"),
                    )
            val useCase =
                GetGroupUseCase(
                    groupRepository = InMemoryGroupRepository(listOf(group)),
                    groupInvitationRepository =
                        InMemoryGroupInvitationRepository(
                            invitations =
                                listOf(
                                    GroupInvitation(
                                        id = groupInvitationId("invitation-1"),
                                        group = groupId("group-1"),
                                        invitedMember = memberEmail("carol"),
                                        invitedBy = memberEmail("alice"),
                                        invitedAt = Instant.parse("2026-04-14T10:00:00Z"),
                                    ),
                                ),
                        ),
                )

            assertEquals(
                GroupSnapshot(
                    group = groupUuid("group-1"),
                    createdBy = "alice@example.com",
                    createdAt = Instant.parse("2026-04-14T08:30:00Z"),
                    members =
                        listOf(
                            GroupMemberSnapshot("alice@example.com", Instant.parse("2026-04-14T08:30:00Z")),
                            GroupMemberSnapshot("bob@example.com", Instant.parse("2026-04-14T09:00:00Z")),
                        ),
                    pendingInvitations =
                        listOf(
                            GroupInvitationSnapshot(
                                invitation = groupInvitationUuid("invitation-1"),
                                invitedMember = "carol@example.com",
                                invitedBy = "alice@example.com",
                                invitedAt = Instant.parse("2026-04-14T10:00:00Z"),
                            ),
                        ),
                ),
                useCase(
                    GetGroupQuery(
                        group = groupUuid("group-1"),
                        requestedBy = memberEmail("bob"),
                    ),
                ),
            )
        }
    }

    @Test
    fun `invoke should fail when the requester is not part of the group`() {
        val useCase =
            GetGroupUseCase(
                groupRepository =
                    InMemoryGroupRepository(
                        groups =
                            listOf(
                                Group.create(
                                    id = groupId("group-1"),
                                    createdBy = memberEmail("alice"),
                                    createdAt = Instant.parse("2026-04-14T08:30:00Z"),
                                ),
                            ),
                    ),
                groupInvitationRepository = InMemoryGroupInvitationRepository(),
            )

        val error =
            assertThrows<GroupAccessDeniedException> {
                runTest {
                    useCase(
                        GetGroupQuery(
                            group = groupUuid("group-1"),
                            requestedBy = memberEmail("bob"),
                        ),
                    )
                }
            }

        assertEquals(
            "member bob@example.com is not part of group ${groupUuid("group-1")}",
            error.message,
        )
    }
}
