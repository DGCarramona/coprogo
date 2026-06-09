package tech.justdev.infrastructure.persistence.group

import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.GroupInvitation
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.GroupInvitationRepository
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.testsupport.PostgresMicronautTest
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.groupInvitationId
import tech.justdev.testsupport.memberEmail
import java.time.Instant

@PostgresMicronautTest
class R2dbcGroupInvitationRepositoryIntegrationTest {
    @Inject
    lateinit var groupInvitationRepository: GroupInvitationRepository

    @Inject
    lateinit var groupRepository: GroupRepository

    @Inject
    lateinit var memberRepository: MemberRepository

    @Test
    fun `persist and pending queries should store a pending group invitation`() =
        runTest {
            memberRepository.persist(
                Member(
                    email = memberEmail("group-owner"),
                    createdAt = Instant.parse("2026-04-14T08:00:00Z"),
                ),
            )
            groupRepository.persist(
                Group.create(
                    id = groupId("group-invitation-repo-1"),
                    createdBy = memberEmail("group-owner"),
                    createdAt = Instant.parse("2026-04-14T08:10:00Z"),
                ),
            )

            val invitation =
                GroupInvitation(
                    id = groupInvitationId("invitation-1"),
                    group = groupId("group-invitation-repo-1"),
                    invitedMember = memberEmail("bob"),
                    invitedBy = memberEmail("group-owner"),
                    invitedAt = Instant.parse("2026-04-14T09:00:00Z"),
                )

            groupInvitationRepository.persist(invitation)

            assertEquals(invitation, groupInvitationRepository.findById(groupInvitationId("invitation-1")))
            assertEquals(listOf(invitation), groupInvitationRepository.findPendingByGroup(groupId("group-invitation-repo-1")))
            assertEquals(listOf(invitation), groupInvitationRepository.findPendingByInvitedMember(memberEmail("bob")))
        }

    @Test
    fun `persist should update acceptance and remove the invitation from pending queries`() =
        runTest {
            memberRepository.persist(
                Member(
                    email = memberEmail("group-owner"),
                    createdAt = Instant.parse("2026-04-14T08:00:00Z"),
                ),
            )
            memberRepository.persist(
                Member(
                    email = memberEmail("bob"),
                    createdAt = Instant.parse("2026-04-14T08:05:00Z"),
                ),
            )
            groupRepository.persist(
                Group.create(
                    id = groupId("group-invitation-repo-2"),
                    createdBy = memberEmail("group-owner"),
                    createdAt = Instant.parse("2026-04-14T08:10:00Z"),
                ),
            )

            val invitation =
                GroupInvitation(
                    id = groupInvitationId("invitation-2"),
                    group = groupId("group-invitation-repo-2"),
                    invitedMember = memberEmail("bob"),
                    invitedBy = memberEmail("group-owner"),
                    invitedAt = Instant.parse("2026-04-14T09:00:00Z"),
                )
            val acceptedInvitation = invitation.accept(memberEmail("bob"), Instant.parse("2026-04-14T10:00:00Z"))

            groupInvitationRepository.persist(invitation)
            groupInvitationRepository.persist(acceptedInvitation)

            assertEquals(acceptedInvitation, groupInvitationRepository.findById(groupInvitationId("invitation-2")))
            assertEquals(emptyList<GroupInvitation>(), groupInvitationRepository.findPendingByGroup(groupId("group-invitation-repo-2")))
            assertEquals(emptyList<GroupInvitation>(), groupInvitationRepository.findPendingByInvitedMember(memberEmail("bob")))
        }
}
