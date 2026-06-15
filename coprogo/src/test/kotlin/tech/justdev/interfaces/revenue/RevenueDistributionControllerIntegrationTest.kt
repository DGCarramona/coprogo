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
import tech.justdev.domain.ledger.event.CashPoolIncomeLedgerEvent
import tech.justdev.domain.ledger.repository.LedgerEventRepository
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.interfaces.group.CreateGroupResponse
import tech.justdev.interfaces.group.InviteMemberRequest
import tech.justdev.testsupport.PostgresMicronautTest
import tech.justdev.testsupport.auth.TestGoogleJwtTokens
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@PostgresMicronautTest
class RevenueDistributionControllerIntegrationTest {
    @Inject
    @field:Client("/")
    lateinit var httpClient: HttpClient

    @Inject
    lateinit var groupInvitationRepository: GroupInvitationRepository

    @Inject
    lateinit var ledgerEventRepository: LedgerEventRepository

    @Nested
    inner class PostGroupOwnershipShares {
        @Test
        fun `the group creator can record ownership shares`() {
            val ownerEmail = "revenue-owner@example.com"
            val coOwnerEmail = "revenue-co-owner@example.com"
            val groupId = createGroup(ownerEmail)
            inviteAndAccept(groupId = groupId, ownerEmail = ownerEmail, invitedEmail = coOwnerEmail)

            val response =
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

            assertEquals(HttpStatus.NO_CONTENT, response.status)
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
    }

    @Nested
    inner class GetGroupOwnershipShares {
        @Test
        fun `a group member can read ownership-share history`() {
            val ownerEmail = "revenue-history-owner@example.com"
            val coOwnerEmail = "revenue-history-co-owner@example.com"
            val groupId = createGroup(ownerEmail)
            inviteAndAccept(groupId = groupId, ownerEmail = ownerEmail, invitedEmail = coOwnerEmail)
            recordOwnershipShares(groupId = groupId, ownerEmail = ownerEmail, coOwnerEmail = coOwnerEmail)

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
        }
    }

    @Nested
    inner class PostGroupRevenueDistributionPreview {
        @Test
        fun `a group member can preview revenue distribution at date`() {
            val ownerEmail = "revenue-preview-owner@example.com"
            val coOwnerEmail = "revenue-preview-co-owner@example.com"
            val groupId = createGroup(ownerEmail)
            inviteAndAccept(groupId = groupId, ownerEmail = ownerEmail, invitedEmail = coOwnerEmail)
            recordOwnershipShares(groupId = groupId, ownerEmail = ownerEmail, coOwnerEmail = coOwnerEmail)

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
    }

    @Nested
    inner class PostGroupCashPoolIncomes {
        @Test
        fun `a group member can record cash-pool income and persist its ledger effects`() {
            val ownerEmail = "income-owner@example.com"
            val coOwnerEmail = "income-co-owner@example.com"
            val groupId = createGroup(ownerEmail)
            inviteAndAccept(groupId = groupId, ownerEmail = ownerEmail, invitedEmail = coOwnerEmail)
            recordOwnershipShares(groupId = groupId, ownerEmail = ownerEmail, coOwnerEmail = coOwnerEmail)

            val response =
                httpClient.toBlocking().exchange(
                    HttpRequest
                        .POST(
                            "/api/groups/$groupId/cash-pool-incomes",
                            RecordCashPoolIncomeRequest(
                                amountInCents = 10_001,
                                receivedAt = Instant.parse("2026-04-03T10:00:00Z"),
                                effectiveDate = LocalDate.parse("2026-04-01"),
                            ),
                        ).header(HttpHeaders.AUTHORIZATION, bearer(coOwnerEmail)),
                    String::class.java,
                )

            assertEquals(HttpStatus.NO_CONTENT, response.status)
            var events = emptyList<tech.justdev.domain.ledger.event.LedgerEvent>()
            runTest {
                events = ledgerEventRepository.findByGroup(GroupId(groupId))
            }
            val income = events.filterIsInstance<CashPoolIncomeLedgerEvent>().single()

            assertEquals(10_001, income.amount.inCents())
            assertEquals(Instant.parse("2026-04-03T10:00:00Z"), income.occurredAt)
            assertEquals(
                mapOf(
                    ownerEmail to 6001L,
                    coOwnerEmail to 4000L,
                ),
                income.allocations.associate { allocation -> allocation.member.toPrimitive() to allocation.amount.inCents() },
            )
        }
    }

    private fun recordOwnershipShares(
        groupId: UUID,
        ownerEmail: String,
        coOwnerEmail: String,
    ) {
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
