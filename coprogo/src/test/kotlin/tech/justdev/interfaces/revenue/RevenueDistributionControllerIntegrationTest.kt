package tech.justdev.interfaces.revenue

import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tech.justdev.domain.group.repository.GroupInvitationRepository
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.interfaces.group.CreateGroupResponse
import tech.justdev.interfaces.group.InviteMemberRequest
import tech.justdev.testsupport.PostgresMicronautTest
import tech.justdev.testsupport.auth.TestGoogleJwtTokens
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@PostgresMicronautTest
class RevenueDistributionControllerIntegrationTest {
    @Inject
    @field:Client("/")
    lateinit var httpClient: HttpClient

    @Inject
    lateinit var groupInvitationRepository: GroupInvitationRepository

    @Test
    fun `the group creator can record ownership shares then preview a distribution at date`() {
        val ownerEmail = "revenue-owner@example.com"
        val coOwnerEmail = "revenue-co-owner@example.com"
        val groupId = createGroup(ownerEmail)
        inviteAndAccept(groupId = groupId, ownerEmail = ownerEmail, invitedEmail = coOwnerEmail)

        val recordResponse =
            httpClient.toBlocking().exchange(
                HttpRequest
                    .POST(
                        "/api/groups/$groupId/ownership-shares",
                        RecordOwnershipShareChangeRequest(
                            effectiveDate = LocalDate.parse("2026-01-01"),
                            shares =
                                setOf(
                                    OwnershipShareInput(ownerEmail, BigDecimal("60.00")),
                                    OwnershipShareInput(coOwnerEmail, BigDecimal("40.00")),
                                ),
                        ),
                    ).header(HttpHeaders.AUTHORIZATION, bearer(ownerEmail)),
                String::class.java,
            )

        assertEquals(HttpStatus.NO_CONTENT, recordResponse.status)

        val history =
            httpClient.toBlocking().retrieve(
                HttpRequest
                    .GET<Any>("/api/groups/$groupId/ownership-shares")
                    .header(HttpHeaders.AUTHORIZATION, bearer(coOwnerEmail)),
                OwnershipShareTimelineResponse::class.java,
            )

        assertEquals(groupId, history.group)
        assertEquals(LocalDate.parse("2026-01-01"), history.changes.single().effectiveDate)
        assertEquals(ownerEmail, history.changes.single().recordedBy)
        assertEquals(
            listOf(
                OwnershipShareResponse(coOwnerEmail, BigDecimal("40.00")),
                OwnershipShareResponse(ownerEmail, BigDecimal("60.00")),
            ),
            history.changes.single().shares,
        )

        val preview =
            httpClient.toBlocking().retrieve(
                HttpRequest
                    .POST(
                        "/api/groups/$groupId/revenue-distribution/preview",
                        RevenueDistributionAtDatePreviewRequest(
                            amountInCents = 10_001,
                            effectiveDate = LocalDate.parse("2026-04-01"),
                        ),
                    ).header(HttpHeaders.AUTHORIZATION, bearer(ownerEmail)),
                RevenueDistributionAtDatePreviewResponse::class.java,
            )

        assertEquals(groupId, preview.group)
        assertEquals(
            listOf(
                RevenueDistributionAllocation(coOwnerEmail, 4000),
                RevenueDistributionAllocation(ownerEmail, 6001),
            ),
            preview.allocations,
        )
    }

    @Test
    fun `a non creator cannot record ownership shares`() {
        val ownerEmail = "revenue-forbidden-owner@example.com"
        val coOwnerEmail = "revenue-forbidden-co-owner@example.com"
        val groupId = createGroup(ownerEmail)
        inviteAndAccept(groupId = groupId, ownerEmail = ownerEmail, invitedEmail = coOwnerEmail)

        val exception =
            assertThrows(HttpClientResponseException::class.java) {
                httpClient.toBlocking().exchange(
                    HttpRequest
                        .POST(
                            "/api/groups/$groupId/ownership-shares",
                            RecordOwnershipShareChangeRequest(
                                effectiveDate = LocalDate.parse("2026-01-01"),
                                shares =
                                    setOf(
                                        OwnershipShareInput(ownerEmail, BigDecimal("50.00")),
                                        OwnershipShareInput(coOwnerEmail, BigDecimal("50.00")),
                                    ),
                            ),
                        ).header(HttpHeaders.AUTHORIZATION, bearer(coOwnerEmail)),
                    String::class.java,
                )
            }

        assertEquals(HttpStatus.FORBIDDEN, exception.status)
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

    private fun inviteAndAccept(
        groupId: UUID,
        ownerEmail: String,
        invitedEmail: String,
    ) {
        httpClient.toBlocking().exchange(
            HttpRequest
                .POST("/api/groups/$groupId/invitations", InviteMemberRequest(invitedMember = invitedEmail))
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerEmail)),
            String::class.java,
        )

        var invitationId: UUID? = null
        runTest {
            invitationId =
                groupInvitationRepository
                    .findPendingByGroup(GroupId(groupId))
                    .single()
                    .id
                    .toPrimitive()
        }

        httpClient.toBlocking().exchange(
            HttpRequest
                .create<Any>(HttpMethod.POST, "/api/group-invitations/${invitationId!!}/accept")
                .header(HttpHeaders.AUTHORIZATION, bearer(invitedEmail)),
            String::class.java,
        )
    }

    private fun bearer(email: String): String = "Bearer ${TestGoogleJwtTokens.googleIdToken(email = email)}"
}
