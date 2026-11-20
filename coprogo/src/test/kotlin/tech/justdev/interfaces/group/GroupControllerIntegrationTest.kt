package tech.justdev.interfaces.group

import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.testsupport.PostgresMicronautTest
import tech.justdev.testsupport.auth.TestGoogleJwtTokens
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
            assertEquals(email, groupResponse.members.single().addedBy)
        }
    }

    @Nested
    inner class PostGroupMembers {
        @Test
        fun `a group member can add another member immediately`() {
            val ownerEmail = "add-owner@example.com"
            val addedEmail = "add-member@example.com"
            persistMember(ownerEmail)
            val groupId = createGroup(ownerEmail)

            val addResponse = addMember(groupId = groupId, ownerEmail = ownerEmail, member = addedEmail)

            assertEquals(HttpStatus.NO_CONTENT, addResponse.status)
            runTest {
                assertEquals(MemberEmail.of(addedEmail), memberRepository.findByEmail(MemberEmail.of(addedEmail))?.email)
                val addedMembership =
                    groupRepository
                        .findById(GroupId(groupId))!!
                        .members
                        .single { membership -> membership.member == MemberEmail.of(addedEmail) }
                assertEquals(MemberEmail.of(ownerEmail), addedMembership.addedBy)
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

    private fun addMember(
        groupId: UUID,
        ownerEmail: String,
        member: String,
    ) = httpClient.toBlocking().exchange(
        HttpRequest
            .POST("/api/groups/$groupId/members", AddGroupMemberRequest(member = member))
            .header(HttpHeaders.AUTHORIZATION, bearer(ownerEmail)),
        String::class.java,
    )

    private fun bearer(email: String): String = "Bearer ${TestGoogleJwtTokens.googleIdToken(email = email)}"
}
