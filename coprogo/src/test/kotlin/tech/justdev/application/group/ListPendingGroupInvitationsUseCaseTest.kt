package tech.justdev.application.group

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tech.justdev.application.support.InMemoryGroupInvitationRepository
import tech.justdev.domain.group.entity.GroupInvitation
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.groupInvitationId
import tech.justdev.testsupport.groupInvitationUuid
import tech.justdev.testsupport.groupUuid
import tech.justdev.testsupport.memberEmail
import java.time.Instant

class ListPendingGroupInvitationsUseCaseTest {
    @Test
    fun `invoke should return pending invitations for the authenticated member sorted by invitation date`() {
        runTest {
            val useCase =
                ListPendingGroupInvitationsUseCase(
                    groupInvitationRepository =
                        InMemoryGroupInvitationRepository(
                            invitations =
                                listOf(
                                    GroupInvitation(
                                        id = groupInvitationId("invitation-2"),
                                        group = groupId("group-2"),
                                        invitedMember = memberEmail("alice"),
                                        invitedBy = memberEmail("bob"),
                                        invitedAt = Instant.parse("2026-04-14T10:00:00Z"),
                                    ),
                                    GroupInvitation(
                                        id = groupInvitationId("invitation-1"),
                                        group = groupId("group-1"),
                                        invitedMember = memberEmail("alice"),
                                        invitedBy = memberEmail("carol"),
                                        invitedAt = Instant.parse("2026-04-14T09:00:00Z"),
                                    ),
                                    GroupInvitation(
                                        id = groupInvitationId("invitation-3"),
                                        group = groupId("group-3"),
                                        invitedMember = memberEmail("dave"),
                                        invitedBy = memberEmail("eve"),
                                        invitedAt = Instant.parse("2026-04-14T11:00:00Z"),
                                    ),
                                ),
                        ),
                )

            assertEquals(
                listOf(
                    PendingGroupInvitationSnapshot(
                        invitation = groupInvitationUuid("invitation-1"),
                        group = groupUuid("group-1"),
                        invitedMember = "alice@example.com",
                        invitedBy = "carol@example.com",
                        invitedAt = Instant.parse("2026-04-14T09:00:00Z"),
                    ),
                    PendingGroupInvitationSnapshot(
                        invitation = groupInvitationUuid("invitation-2"),
                        group = groupUuid("group-2"),
                        invitedMember = "alice@example.com",
                        invitedBy = "bob@example.com",
                        invitedAt = Instant.parse("2026-04-14T10:00:00Z"),
                    ),
                ),
                useCase(
                    ListPendingGroupInvitationsQuery(
                        requestedBy = memberEmail("alice"),
                    ),
                ),
            )
        }
    }
}
