package tech.justdev.interfaces.group

import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.core.type.Argument
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tech.justdev.domain.group.entity.GroupInvitation
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.GroupInvitationRepository
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.testsupport.UsesPostgresTestDatabase
import tech.justdev.testsupport.auth.TestGoogleJwtTokens
import tech.justdev.testsupport.memberEmail
import java.time.Instant

@MicronautTest(transactional = false)
@UsesPostgresTestDatabase
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

    @Test
    fun `a new user can create a group on the first authenticated request`() {
        val email = "new-owner@example.com"
        val createRequest =
            HttpRequest
                .create<Any>(HttpMethod.POST, "/api/groups")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestGoogleJwtTokens.googleIdToken(email = email)}")

        val createResponse = httpClient.toBlocking().exchange(createRequest, CreateGroupResponse::class.java)

        assertEquals(HttpStatus.CREATED, createResponse.status)
        val groupId = createResponse.body()!!.group
        val groupResponse =
            httpClient.toBlocking().retrieve(
                HttpRequest
                    .GET<Any>("/api/groups/$groupId")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestGoogleJwtTokens.googleIdToken(email = email)}"),
                GroupResponse::class.java,
            )

        assertEquals(email, groupResponse.createdBy)
        assertEquals(1, groupResponse.members.size)
        assertEquals(email, groupResponse.members.single().member)
    }

    @Test
    fun `an invited user can accept the invitation without explicit prior registration`() {
        val ownerEmail = "owner@example.com"
        val invitedEmail = "invited@example.com"
        runTest {
            memberRepository.persist(
                Member(
                    email = MemberEmail.of(ownerEmail),
                    createdAt = Instant.parse("2026-04-14T08:00:00Z"),
                ),
            )
        }

        val createGroupResponse =
            httpClient.toBlocking().exchange(
                HttpRequest
                    .create<Any>(HttpMethod.POST, "/api/groups")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestGoogleJwtTokens.googleIdToken(email = ownerEmail)}"),
                CreateGroupResponse::class.java,
            )
        val groupId = createGroupResponse.body()!!.group

        val inviteResponse =
            httpClient.toBlocking().exchange(
                HttpRequest
                    .POST("/api/groups/$groupId/invitations", InviteMemberRequest(invitedMember = invitedEmail))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestGoogleJwtTokens.googleIdToken(email = ownerEmail)}"),
                String::class.java,
            )

        assertEquals(HttpStatus.NO_CONTENT, inviteResponse.status)

        var invitationId: java.util.UUID? = null
        runTest {
            invitationId =
                groupInvitationRepository
                    .findPendingByGroup(GroupId(groupId))
                    .single()
                    .id
                    .toPrimitive()
        }

        val acceptResponse =
            httpClient.toBlocking().exchange(
                HttpRequest
                    .create<Any>(HttpMethod.POST, "/api/group-invitations/${invitationId!!}/accept")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestGoogleJwtTokens.googleIdToken(email = invitedEmail)}"),
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

    @Test
    fun `the authenticated member can list pending invitations on first connection`() {
        val ownerEmail = "owner@example.com"
        val invitedEmail = "invited@example.com"
        runTest {
            memberRepository.persist(
                Member(
                    email = MemberEmail.of(ownerEmail),
                    createdAt = Instant.parse("2026-04-14T08:00:00Z"),
                ),
            )
        }

        val createGroupResponse =
            httpClient.toBlocking().exchange(
                HttpRequest
                    .create<Any>(HttpMethod.POST, "/api/groups")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestGoogleJwtTokens.googleIdToken(email = ownerEmail)}"),
                CreateGroupResponse::class.java,
            )
        val groupId = createGroupResponse.body()!!.group

        httpClient.toBlocking().exchange(
            HttpRequest
                .POST("/api/groups/$groupId/invitations", InviteMemberRequest(invitedMember = invitedEmail))
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestGoogleJwtTokens.googleIdToken(email = ownerEmail)}"),
            String::class.java,
        )

        val pendingInvitations =
            httpClient.toBlocking().retrieve(
                HttpRequest
                    .GET<Any>("/api/group-invitations/pending")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestGoogleJwtTokens.googleIdToken(email = invitedEmail)}"),
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
