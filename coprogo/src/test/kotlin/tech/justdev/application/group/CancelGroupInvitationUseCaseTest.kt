package tech.justdev.application.group

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.application.shared.DirectTransactionRunner
import tech.justdev.application.support.InMemoryGroupInvitationRepository
import tech.justdev.application.support.InMemoryGroupRepository
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.GroupInvitation
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.groupInvitationId
import tech.justdev.testsupport.memberEmail
import java.time.Instant

class CancelGroupInvitationUseCaseTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val group =
        Group
            .create(groupId("one"), memberEmail("alice"), now)
            .addMember(memberEmail("bob"), now)
            .addMember(memberEmail("carol"), now)
    private val invitation = GroupInvitation(groupInvitationId("one"), group.id, memberEmail("dana"), memberEmail("bob"), now)
    private val invitations = InMemoryGroupInvitationRepository(listOf(invitation))
    private val useCase =
        CancelGroupInvitationUseCaseImpl(
            GroupAccessPolicy(InMemoryGroupRepository(listOf(group))),
            invitations,
            DirectTransactionRunner,
        )

    @Nested
    inner class Invoke {
        @Test
        fun `sender can cancel and invitee can no longer find invitation`() =
            runTest {
                useCase(CancelGroupInvitationCommand(group.id, invitation.id, memberEmail("bob")))
                assertEquals(null, invitations.findById(invitation.id))
                assertEquals(emptyList<GroupInvitation>(), invitations.findPendingByInvitedMember(invitation.invitedMember))
            }

        @Test
        fun `creator can cancel an invitation sent by another member`() =
            runTest {
                useCase(CancelGroupInvitationCommand(group.id, invitation.id, memberEmail("alice")))
                assertEquals(null, invitations.findById(invitation.id))
            }

        @Test
        fun `other member cannot cancel`() {
            assertThrows<GroupInvitationCancellationDeniedException> {
                runTest {
                    useCase(CancelGroupInvitationCommand(group.id, invitation.id, memberEmail("carol")))
                }
            }
            runTest { assertEquals(invitation, invitations.findById(invitation.id)) }
        }

        @Test
        fun `outsider cannot cancel`() {
            assertThrows<GroupAccessDeniedException> {
                runTest {
                    useCase(CancelGroupInvitationCommand(group.id, invitation.id, memberEmail("dana")))
                }
            }
        }

        @Test
        fun `invitation from another group cannot be cancelled`() {
            runTest { invitations.persist(invitation.copy(group = groupId("other"))) }
            assertThrows<GroupInvitationNotFoundException> {
                runTest {
                    useCase(CancelGroupInvitationCommand(group.id, invitation.id, memberEmail("alice")))
                }
            }
        }

        @Test
        fun `missing invitation cannot be cancelled`() {
            assertThrows<GroupInvitationNotFoundException> {
                runTest {
                    useCase(CancelGroupInvitationCommand(group.id, groupInvitationId("missing"), memberEmail("alice")))
                }
            }
        }

        @Test
        fun `accepted invitation cannot be cancelled`() {
            val accepted = invitation.accept(invitation.invitedMember, now)
            runTest { invitations.persist(accepted) }
            assertThrows<GroupInvitationAlreadyAcceptedException> {
                runTest {
                    useCase(CancelGroupInvitationCommand(group.id, invitation.id, memberEmail("alice")))
                }
            }
            runTest { assertEquals(accepted, invitations.findById(invitation.id)) }
        }
    }
}
