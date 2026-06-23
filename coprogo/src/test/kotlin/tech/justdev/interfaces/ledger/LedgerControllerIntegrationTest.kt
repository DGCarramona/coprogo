package tech.justdev.interfaces.ledger

import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.ledger.effect.MemberBalanceTransfer
import tech.justdev.domain.ledger.event.AcceptedExpenseLedgerEvent
import tech.justdev.domain.ledger.event.CashPoolIncomeLedgerEvent
import tech.justdev.domain.ledger.event.CashPoolWithdrawalLedgerEvent
import tech.justdev.domain.ledger.repository.LedgerEventRepository
import tech.justdev.domain.revenue.valueobject.OwnershipPercentage
import tech.justdev.domain.revenue.valueobject.OwnershipShare
import tech.justdev.domain.revenue.valueobject.RevenueDistribution
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.testsupport.PostgresMicronautTest
import tech.justdev.testsupport.acceptedExpenseLedgerEventId
import tech.justdev.testsupport.auth.TestGoogleJwtTokens
import tech.justdev.testsupport.expenseId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.ledgerEventId
import java.time.Instant
import java.util.UUID

@PostgresMicronautTest
class LedgerControllerIntegrationTest {
    @Inject
    @field:Client("/")
    lateinit var httpClient: HttpClient

    @Inject
    lateinit var ledgerEventRepository: LedgerEventRepository

    @Inject
    lateinit var memberRepository: MemberRepository

    @Inject
    lateinit var groupRepository: GroupRepository

    @Nested
    inner class GetGroupBalances {
        @Test
        fun `a group member can read projected member balances`() {
            val ownerEmail = "ledger-balances-owner@example.com"
            val coOwnerEmail = "ledger-balances-co-owner@example.com"
            val groupId = persistGroup(seed = "ledger-balances", ownerEmail = ownerEmail, coOwnerEmail = coOwnerEmail)
            appendAcceptedExpense(groupId = groupId, ownerEmail = ownerEmail, coOwnerEmail = coOwnerEmail)

            val response =
                httpClient.toBlocking().retrieve(
                    HttpRequest
                        .GET<Any>("/api/groups/$groupId/balances")
                        .header(HttpHeaders.AUTHORIZATION, bearer(coOwnerEmail)),
                    GroupBalancesResponse::class.java,
                )

            assertEquals(groupId, response.group)
            assertEquals(
                listOf(
                    GroupMemberBalanceResponse(coOwnerEmail, -4000),
                    GroupMemberBalanceResponse(ownerEmail, 4000),
                ),
                response.balances,
            )
        }
    }

    @Nested
    inner class GetCashPoolBalance {
        @Test
        fun `a group member can read the available cash-pool balance`() {
            val ownerEmail = "ledger-cash-owner@example.com"
            val coOwnerEmail = "ledger-cash-co-owner@example.com"
            val groupId = persistGroup(seed = "ledger-cash", ownerEmail = ownerEmail, coOwnerEmail = coOwnerEmail)
            appendCashPoolEvents(groupId = groupId, ownerEmail = ownerEmail, coOwnerEmail = coOwnerEmail)

            val response =
                httpClient.toBlocking().retrieve(
                    HttpRequest
                        .GET<Any>("/api/groups/$groupId/cash-pool/balance")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerEmail)),
                    CashPoolBalanceResponse::class.java,
                )

            assertEquals(CashPoolBalanceResponse(group = groupId, availableAmountInCents = 7500), response)
        }
    }

    @Nested
    inner class GetMemberCashPoolShares {
        @Test
        fun `a group member can read remaining cash-pool shares by member`() {
            val ownerEmail = "ledger-shares-owner@example.com"
            val coOwnerEmail = "ledger-shares-co-owner@example.com"
            val groupId = persistGroup(seed = "ledger-shares", ownerEmail = ownerEmail, coOwnerEmail = coOwnerEmail)
            appendCashPoolEvents(groupId = groupId, ownerEmail = ownerEmail, coOwnerEmail = coOwnerEmail)

            val response =
                httpClient.toBlocking().retrieve(
                    HttpRequest
                        .GET<Any>("/api/groups/$groupId/cash-pool/shares")
                        .header(HttpHeaders.AUTHORIZATION, bearer(coOwnerEmail)),
                    GroupMemberCashPoolSharesResponse::class.java,
                )

            assertEquals(groupId, response.group)
            assertEquals(
                listOf(
                    MemberCashPoolShareResponse(coOwnerEmail, 4000),
                    MemberCashPoolShareResponse(ownerEmail, 3500),
                ),
                response.shares,
            )
        }
    }

    private fun appendAcceptedExpense(
        groupId: UUID,
        ownerEmail: String,
        coOwnerEmail: String,
    ) {
        runTest {
            ledgerEventRepository.append(
                AcceptedExpenseLedgerEvent(
                    id = acceptedExpenseLedgerEventId("ledger-controller-expense-$groupId"),
                    group = GroupId(groupId),
                    expense = expenseId("ledger-controller-expense-$groupId"),
                    paidBy = MemberEmail.of(ownerEmail),
                    occurredAt = Instant.parse("2026-04-03T10:00:00Z"),
                    transfers =
                        setOf(
                            MemberBalanceTransfer(
                                fromMember = MemberEmail.of(coOwnerEmail),
                                toMember = MemberEmail.of(ownerEmail),
                                amount = MoneyAmount.ofCents(4000),
                            ),
                        ),
                ),
            )
        }
    }

    private fun appendCashPoolEvents(
        groupId: UUID,
        ownerEmail: String,
        coOwnerEmail: String,
    ) {
        runTest {
            val group = GroupId(groupId)
            val owner = MemberEmail.of(ownerEmail)
            val coOwner = MemberEmail.of(coOwnerEmail)
            ledgerEventRepository.append(
                CashPoolIncomeLedgerEvent.from(
                    id = ledgerEventId("ledger-controller-income-$groupId"),
                    group = group,
                    occurredAt = Instant.parse("2026-04-03T10:00:00Z"),
                    distribution =
                        RevenueDistribution.distribute(
                            totalAmount = MoneyAmount.ofCents(10_000),
                            ownershipShares =
                                setOf(
                                    OwnershipShare(owner, OwnershipPercentage.ofBasisPoints(6000)),
                                    OwnershipShare(coOwner, OwnershipPercentage.ofBasisPoints(4000)),
                                ),
                        ),
                ),
            )
            ledgerEventRepository.append(
                CashPoolWithdrawalLedgerEvent(
                    id = ledgerEventId("ledger-controller-withdrawal-$groupId"),
                    group = group,
                    withdrawnBy = owner,
                    withdrawnAmount = MoneyAmount.ofCents(2500),
                    ownRevenueShareConsumed = MoneyAmount.ofCents(2500),
                    balanceTransfers = emptySet(),
                    occurredAt = Instant.parse("2026-04-03T12:00:00Z"),
                ),
            )
        }
    }

    private fun persistGroup(
        seed: String,
        ownerEmail: String,
        coOwnerEmail: String,
    ): UUID {
        val group =
            Group
                .create(
                    id = groupId(seed),
                    createdBy = MemberEmail.of(ownerEmail),
                    createdAt = Instant.parse("2026-04-03T08:00:00Z"),
                ).addMember(
                    member = MemberEmail.of(coOwnerEmail),
                    joinedAt = Instant.parse("2026-04-03T08:01:00Z"),
                )

        runTest {
            memberRepository.persist(Member(email = MemberEmail.of(ownerEmail), createdAt = Instant.parse("2026-04-03T07:58:00Z")))
            memberRepository.persist(Member(email = MemberEmail.of(coOwnerEmail), createdAt = Instant.parse("2026-04-03T07:59:00Z")))
            groupRepository.persist(group)
        }

        return group.id.toPrimitive()
    }

    private fun bearer(email: String): String = "Bearer ${TestGoogleJwtTokens.googleIdToken(email = email)}"
}
