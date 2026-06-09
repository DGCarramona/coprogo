package tech.justdev.infrastructure.persistence.group

import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
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

    @Nested
    inner class Persist {
        @Test
        fun `should store a pending group invitation`() =
            runTest {
                val invitation = invitationFixture("pa").invitation

                groupInvitationRepository.persist(invitation)

                assertEquals(invitation, groupInvitationRepository.findById(invitation.id))
            }

        @Test
        fun `should update acceptance and remove the invitation from pending queries`() =
            runTest {
                val fixture = invitationFixture("pb", persistInvitedMember = true)
                val acceptedInvitation = fixture.invitation.accept(fixture.invitedMember, Instant.parse("2026-04-14T10:00:00Z"))

                groupInvitationRepository.persist(fixture.invitation)
                groupInvitationRepository.persist(acceptedInvitation)

                assertEquals(acceptedInvitation, groupInvitationRepository.findById(fixture.invitation.id))
                assertEquals(emptyList<GroupInvitation>(), groupInvitationRepository.findPendingByGroup(fixture.group.id))
                assertEquals(emptyList<GroupInvitation>(), groupInvitationRepository.findPendingByInvitedMember(fixture.invitedMember))
            }
    }

    @Nested
    inner class FindById {
        @Test
        fun `should find a persisted invitation`() =
            runTest {
                val invitation = invitationFixture("fa").invitation
                groupInvitationRepository.persist(invitation)

                assertEquals(invitation, groupInvitationRepository.findById(invitation.id))
            }

        @Test
        fun `should return null when no invitation exists for the id`() =
            runTest {
                assertEquals(null, groupInvitationRepository.findById(groupInvitationId("missing-inv")))
            }
    }

    @Nested
    inner class FindPendingByGroup {
        @Test
        fun `should return pending invitations for the group`() =
            runTest {
                val fixture = invitationFixture("ga")
                groupInvitationRepository.persist(fixture.invitation)

                assertEquals(listOf(fixture.invitation), groupInvitationRepository.findPendingByGroup(fixture.group.id))
            }

        @Test
        fun `should not return accepted invitations`() =
            runTest {
                val fixture = invitationFixture("gb", persistInvitedMember = true)
                groupInvitationRepository.persist(fixture.invitation.accept(fixture.invitedMember, Instant.parse("2026-04-14T10:00:00Z")))

                assertEquals(emptyList<GroupInvitation>(), groupInvitationRepository.findPendingByGroup(fixture.group.id))
            }
    }

    @Nested
    inner class FindPendingByInvitedMember {
        @Test
        fun `should return pending invitations for the invited member`() =
            runTest {
                val fixture = invitationFixture("ia")
                groupInvitationRepository.persist(fixture.invitation)

                assertEquals(listOf(fixture.invitation), groupInvitationRepository.findPendingByInvitedMember(fixture.invitedMember))
            }

        @Test
        fun `should not return accepted invitations`() =
            runTest {
                val fixture = invitationFixture("ib", persistInvitedMember = true)
                groupInvitationRepository.persist(fixture.invitation.accept(fixture.invitedMember, Instant.parse("2026-04-14T10:00:00Z")))

                assertEquals(emptyList<GroupInvitation>(), groupInvitationRepository.findPendingByInvitedMember(fixture.invitedMember))
            }
    }

    private suspend fun invitationFixture(
        seed: String,
        persistInvitedMember: Boolean = false,
    ): GroupInvitationFixture {
        val owner = memberEmail("$seed-owner")
        val invitedMember = memberEmail("$seed-invited")
        memberRepository.persist(Member(email = owner, createdAt = Instant.parse("2026-04-14T08:00:00Z")))
        if (persistInvitedMember) {
            memberRepository.persist(Member(email = invitedMember, createdAt = Instant.parse("2026-04-14T08:05:00Z")))
        }

        val group =
            Group.create(
                id = groupId("$seed-group"),
                createdBy = owner,
                createdAt = Instant.parse("2026-04-14T08:10:00Z"),
            )
        groupRepository.persist(group)

        return GroupInvitationFixture(
            group = group,
            invitedMember = invitedMember,
            invitation =
                GroupInvitation(
                    id = groupInvitationId("$seed-inv"),
                    group = group.id,
                    invitedMember = invitedMember,
                    invitedBy = owner,
                    invitedAt = Instant.parse("2026-04-14T09:00:00Z"),
                ),
        )
    }

    private data class GroupInvitationFixture(
        val group: Group,
        val invitedMember: tech.justdev.domain.group.valueobject.MemberEmail,
        val invitation: GroupInvitation,
    )
}
