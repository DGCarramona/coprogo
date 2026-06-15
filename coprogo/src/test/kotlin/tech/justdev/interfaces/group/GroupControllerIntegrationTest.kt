package tech.justdev.interfaces.group

import io.micronaut.core.type.Argument
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tech.justdev.domain.group.entity.GroupInvitation
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.GroupInvitationRepository
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.testsupport.PostgresMicronautTest
import tech.justdev.testsupport.auth.TestGoogleJwtTokens
import tech.justdev.testsupport.memberEmail
import java.time.Instant
import java.util.UUID

@PostgresMicronautTest
class GroupControllerIntegrationTest {
    @Inject
    @field:Client("/")
    lateinit var httpClient: HttpClient

    @Inject
    lateinit var memberRepository: MemberRepository

    @Inject
    lateinit var groupRepository: GroupRepository

    @Inject
    lateinit var groupInvitationRepository: GroupInvitationRepository

    @Nested
    inner class PostGroups {
        @Test
        fun `a new user can create a group on the first authenticated request`() {
            val email = "new-owner@example.com"
            val createRequest =
                HttpRequest
                    .create<Any>(HttpMethod.POST, "/api/groups")
                    .header(HttpHeaders.AUTHORIZATION, bearer(email))

            val createResponse = httpClient.toBlocking().exchange(createRequest, CreateGroupResponse::class.java)

            assertEquals(HttpStatus.CREATED, createResponse.status)
            createResponse.body()!!.group
        }
    }

    @Nested
    inner class GetGroupsById {
        @Test
        fun `a group member can retrieve a created group`() {
            val email = "get-group-owner@example.com"
            val groupId = createGroup(email)

            val groupResponse =
                httpClient.toBlocking().retrieve(
                    HttpRequest
                        .GET<Any>("/api/groups/$groupId")
                        .header(HttpHeaders.AUTHORIZATION, bearer(email)),
                    GroupResponse::class.java,
                )

            assertEquals(email, groupResponse.createdBy)
            assertEquals(1, groupResponse.members.size)
            assertEquals(email, groupResponse.members.single().member)
        }
    }

    @Nested
    inner class PostGroupInvitations {
        @Test
        fun `a group member can invite another member`() {
            val ownerEmail = "invite-owner@example.com"
            val invitedEmail = "invite-invited@example.com"
            persistMember(ownerEmail)
            val groupId = createGroup(ownerEmail)

            val inviteResponse = inviteMember(groupId = groupId, ownerEmail = ownerEmail, invitedEmail = invitedEmail)

            assertEquals(HttpStatus.NO_CONTENT, inviteResponse.status)
            runTest {
                assertEquals(
                    invitedEmail,
                    groupInvitationRepository
                        .findPendingByGroup(GroupId(groupId))
                        .single()
                        .invitedMember
                        .toPrimitive(),
                )
            }
        }
    }

    @Nested
    inner class PostGroupInvitationAccept {
        @Test
        fun `an invited user can accept the invitation without explicit prior registration`() {
            val ownerEmail = "owner@example.com"
            val invitedEmail = "invited@example.com"
            persistMember(ownerEmail)
            val groupId = createGroup(ownerEmail)
            inviteMember(groupId = groupId, ownerEmail = ownerEmail, invitedEmail = invitedEmail)
            val invitationId = pendingInvitationId(groupId)

            val acceptResponse =
                httpClient.toBlocking().exchange(
                    HttpRequest
                        .create<Any>(HttpMethod.POST, "/api/group-invitations/$invitationId/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(invitedEmail)),
                    String::class.java,
                )

            assertEquals(HttpStatus.NO_CONTENT, acceptResponse.status)
            runTest {
                assertEquals(MemberEmail.of(invitedEmail), memberRepository.findByEmail(MemberEmail.of(invitedEmail))?.email)
                val members = groupRepository.findById(GroupId(groupId))!!.members
                assertTrue(members.any { member -> member.member == memberEmail("invited") })
                assertEquals(emptyList<GroupInvitation>(), groupInvitationRepository.findPendingByGroup(GroupId(groupId)))
            }
        }
    }

    @Nested
    inner class GetGroupInvitationsPending {
        @Test
        fun `the authenticated member can list pending invitations on first connection`() {
            val ownerEmail = "pending-owner@example.com"
            val invitedEmail = "pending-invited@example.com"
            persistMember(ownerEmail)
            val groupId = createGroup(ownerEmail)
            inviteMember(groupId = groupId, ownerEmail = ownerEmail, invitedEmail = invitedEmail)

            val pendingInvitations =
                httpClient.toBlocking().retrieve(
                    HttpRequest
                        .GET<Any>("/api/group-invitations/pending")
                        .header(HttpHeaders.AUTHORIZATION, bearer(invitedEmail)),
                    Argument.listOf(PendingGroupInvitationResponse::class.java),
                )

            assertEquals(
                listOf(
                    PendingGroupInvitationResponse(
                        invitation = pendingInvitations.single().invitation,
                        group = groupId,
                        invitedMember = invitedEmail,
                        invitedBy = ownerEmail,
                        invitedAt = pendingInvitations.single().invitedAt,
                    ),
                ),
                pendingInvitations,
            )
            runTest {
                assertEquals(MemberEmail.of(invitedEmail), memberRepository.findByEmail(MemberEmail.of(invitedEmail))?.email)
            }
        }
    }

    private fun persistMember(email: String) {
        runTest {
            memberRepository.persist(
                Member(
                    email = MemberEmail.of(email),
                    createdAt = Instant.parse("2026-04-14T08:00:00Z"),
                ),
            )
        }
    }

    private fun createGroup(ownerEmail: String): UUID =
        httpClient
            .toBlocking()
            .exchange(
                HttpRequest
                    .create<Any>(HttpMethod.POST, "/api/groups")
                    .header(HttpHeaders.AUTHORIZATION, bearer(ownerEmail)),
                CreateGroupResponse::class.java,
            ).body()!!
            .group

    private fun inviteMember(
        groupId: UUID,
        ownerEmail: String,
        invitedEmail: String,
    ) = httpClient.toBlocking().exchange(
        HttpRequest
            .POST("/api/groups/$groupId/invitations", InviteMemberRequest(invitedMember = invitedEmail))
            .header(HttpHeaders.AUTHORIZATION, bearer(ownerEmail)),
        String::class.java,
    )

    private fun pendingInvitationId(groupId: UUID): UUID {
        var invitationId: UUID? = null
        runTest {
            invitationId =
                groupInvitationRepository
                    .findPendingByGroup(GroupId(groupId))
                    .single()
                    .id
                    .toPrimitive()
        }
        return invitationId!!
    }

    private fun bearer(email: String): String = "Bearer ${TestGoogleJwtTokens.googleIdToken(email = email)}"
}
