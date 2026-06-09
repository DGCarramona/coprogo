package tech.justdev.application.group

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.application.support.InMemoryGroupInvitationRepository
import tech.justdev.application.support.InMemoryGroupRepository
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.GroupInvitation
import tech.justdev.testsupport.FixedGroupInvitationIdGenerator
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.groupInvitationId
import tech.justdev.testsupport.groupUuid
import tech.justdev.testsupport.memberEmail
import java.time.Instant

class InviteMemberToGroupUseCaseTest {
    @Test
    fun `invoke should create a pending invitation when the requester is a group member`() {
        runTest {
            val groupRepository =
                InMemoryGroupRepository(
                    groups =
                        listOf(
                            Group.create(
                                id = groupId("group-1"),
                                createdBy = memberEmail("alice"),
                                createdAt = Instant.parse("2026-04-14T08:00:00Z"),
                            ),
                        ),
                )
            val invitationRepository = InMemoryGroupInvitationRepository()
            val useCase =
                InviteMemberToGroupUseCase(
                    groupAccessPolicy = GroupAccessPolicy(groupRepository),
                    groupInvitationRepository = invitationRepository,
                    groupInvitationIdGenerator = FixedGroupInvitationIdGenerator(listOf(groupInvitationId("invitation-1"))),
                )

            useCase(
                InviteMemberToGroupCommand(
                    group = groupUuid("group-1"),
                    invitedBy = memberEmail("alice"),
                    invitedMember = memberEmail("bob"),
                    invitedAt = Instant.parse("2026-04-14T10:00:00Z"),
                ),
            )

            assertEquals(
                GroupInvitation(
                    id = groupInvitationId("invitation-1"),
                    group = groupId("group-1"),
                    invitedMember = memberEmail("bob"),
                    invitedBy = memberEmail("alice"),
                    invitedAt = Instant.parse("2026-04-14T10:00:00Z"),
                ),
                invitationRepository.findById(groupInvitationId("invitation-1")),
            )
        }
    }

    @Test
    fun `invoke should fail when the requester is not part of the group`() {
        val useCase =
            InviteMemberToGroupUseCase(
                groupAccessPolicy =
                    GroupAccessPolicy(
                        InMemoryGroupRepository(
                            groups =
                                listOf(
                                    Group.create(
                                        id = groupId("group-1"),
                                        createdBy = memberEmail("alice"),
                                        createdAt = Instant.parse("2026-04-14T08:00:00Z"),
                                    ),
                                ),
                        ),
                    ),
                groupInvitationRepository = InMemoryGroupInvitationRepository(),
                groupInvitationIdGenerator = FixedGroupInvitationIdGenerator(listOf(groupInvitationId("invitation-1"))),
            )

        val error =
            assertThrows<GroupAccessDeniedException> {
                runTest {
                    useCase(
                        InviteMemberToGroupCommand(
                            group = groupUuid("group-1"),
                            invitedBy = memberEmail("bob"),
                            invitedMember = memberEmail("carol"),
                            invitedAt = Instant.parse("2026-04-14T10:00:00Z"),
                        ),
                    )
                }
            }

        assertEquals(
            "member bob@example.com is not part of group ${groupUuid("group-1")}",
            error.message,
        )
    }

    @Test
    fun `invoke should fail when a pending invitation already exists for the email`() {
        val useCase =
            InviteMemberToGroupUseCase(
                groupAccessPolicy =
                    GroupAccessPolicy(
                        InMemoryGroupRepository(
                            groups =
                                listOf(
                                    Group.create(
                                        id = groupId("group-1"),
                                        createdBy = memberEmail("alice"),
                                        createdAt = Instant.parse("2026-04-14T08:00:00Z"),
                                    ),
                                ),
                        ),
                    ),
                groupInvitationRepository =
                    InMemoryGroupInvitationRepository(
                        invitations =
                            listOf(
                                GroupInvitation(
                                    id = groupInvitationId("invitation-1"),
                                    group = groupId("group-1"),
                                    invitedMember = memberEmail("bob"),
                                    invitedBy = memberEmail("alice"),
                                    invitedAt = Instant.parse("2026-04-14T09:00:00Z"),
                                ),
                            ),
                    ),
                groupInvitationIdGenerator = FixedGroupInvitationIdGenerator(listOf(groupInvitationId("invitation-2"))),
            )

        val error =
            assertThrows<GroupInvitationAlreadyExistsException> {
                runTest {
                    useCase(
                        InviteMemberToGroupCommand(
                            group = groupUuid("group-1"),
                            invitedBy = memberEmail("alice"),
                            invitedMember = memberEmail("bob"),
                            invitedAt = Instant.parse("2026-04-14T10:00:00Z"),
                        ),
                    )
                }
            }

        assertEquals(
            "group ${groupUuid("group-1")} already has a pending invitation for bob@example.com",
            error.message,
        )
    }
}
