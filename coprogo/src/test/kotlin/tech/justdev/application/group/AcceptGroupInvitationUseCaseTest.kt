package tech.justdev.application.group

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.application.support.InMemoryGroupInvitationRepository
import tech.justdev.application.support.InMemoryGroupRepository
import tech.justdev.application.support.InMemoryMemberRepository
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.GroupInvitation
import tech.justdev.domain.group.entity.GroupMember
import tech.justdev.domain.group.entity.Member
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.groupInvitationId
import tech.justdev.testsupport.groupInvitationUuid
import tech.justdev.testsupport.memberEmail
import java.time.Instant

class AcceptGroupInvitationUseCaseTest {
    @Test
    fun `invoke should accept the invitation, add the member to the group and auto-register the member`() {
        runTest {
            val acceptedAt = Instant.parse("2026-04-14T11:00:00Z")
            val memberRepository =
                InMemoryMemberRepository(
                    members =
                        listOf(
                            Member(
                                email = memberEmail("alice"),
                                createdAt = Instant.parse("2026-04-14T08:00:00Z"),
                            ),
                        ),
                )
            val groupRepository =
                InMemoryGroupRepository(
                    groups =
                        listOf(
                            Group.create(
                                id = groupId("group-1"),
                                createdBy = memberEmail("alice"),
                                createdAt = Instant.parse("2026-04-14T08:30:00Z"),
                            ),
                        ),
                )
            val invitationRepository =
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
                )
            val useCase =
                AcceptGroupInvitationUseCase(
                    memberRepository = memberRepository,
                    groupRepository = groupRepository,
                    groupInvitationRepository = invitationRepository,
                )

            useCase(
                AcceptGroupInvitationCommand(
                    invitation = groupInvitationUuid("invitation-1"),
                    acceptedBy = memberEmail("bob"),
                    acceptedAt = acceptedAt,
                ),
            )

            assertEquals(
                Member(
                    email = memberEmail("bob"),
                    createdAt = acceptedAt,
                ),
                memberRepository.findByEmail(memberEmail("bob")),
            )
            assertEquals(
                setOf(
                    GroupMember(member = memberEmail("alice"), joinedAt = Instant.parse("2026-04-14T08:30:00Z")),
                    GroupMember(member = memberEmail("bob"), joinedAt = acceptedAt),
                ),
                groupRepository.findById(groupId("group-1"))?.members,
            )
            assertEquals(
                GroupInvitation(
                    id = groupInvitationId("invitation-1"),
                    group = groupId("group-1"),
                    invitedMember = memberEmail("bob"),
                    invitedBy = memberEmail("alice"),
                    invitedAt = Instant.parse("2026-04-14T09:00:00Z"),
                    acceptedBy = memberEmail("bob"),
                    acceptedAt = acceptedAt,
                ),
                invitationRepository.findById(groupInvitationId("invitation-1")),
            )
        }
    }

    @Test
    fun `invoke should fail when another member tries to accept the invitation`() {
        val useCase =
            AcceptGroupInvitationUseCase(
                memberRepository = InMemoryMemberRepository(),
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
            )

        val error =
            assertThrows<GroupInvitationAccessDeniedException> {
                runTest {
                    useCase(
                        AcceptGroupInvitationCommand(
                            invitation = groupInvitationUuid("invitation-1"),
                            acceptedBy = memberEmail("carol"),
                            acceptedAt = Instant.parse("2026-04-14T11:00:00Z"),
                        ),
                    )
                }
            }

        assertEquals(
            "member carol@example.com cannot accept group invitation ${groupInvitationUuid("invitation-1")}",
            error.message,
        )
    }
}
