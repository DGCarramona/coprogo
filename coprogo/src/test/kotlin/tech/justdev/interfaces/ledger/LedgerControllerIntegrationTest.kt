package tech.justdev.interfaces.ledger

import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.ledger.effect.MemberBalanceTransfer
import tech.justdev.domain.ledger.event.AcceptedExpenseLedgerEvent
import tech.justdev.domain.ledger.event.AcceptedReimbursementLedgerEvent
import tech.justdev.domain.ledger.event.CashPoolIncomeLedgerEvent
import tech.justdev.domain.ledger.event.CashPoolWithdrawalLedgerEvent
import tech.justdev.domain.ledger.repository.LedgerEventRepository
import tech.justdev.domain.reimbursement.entity.Reimbursement
import tech.justdev.domain.reimbursement.repository.ReimbursementRepository
import tech.justdev.domain.reimbursement.valueobject.ReimbursementId
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
import tech.justdev.testsupport.testUuid
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

    @Inject
    lateinit var reimbursementRepository: ReimbursementRepository

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

    @Nested
    inner class WithdrawFromCashPool {
        @Test
        fun `a withdrawal is persisted and updates every cash-pool projection`() {
            val ownerEmail = "ledger-withdrawal-owner@example.com"
            val coOwnerEmail = "ledger-withdrawal-co-owner@example.com"
            val groupId = persistGroup(seed = "ledger-withdrawal", ownerEmail = ownerEmail, coOwnerEmail = coOwnerEmail)
            appendCashPoolIncome(groupId = groupId, ownerEmail = ownerEmail, coOwnerEmail = coOwnerEmail)

            val response =
                httpClient.toBlocking().exchange(
                    HttpRequest
                        .POST(
                            "/api/groups/$groupId/cash-pool-withdrawals",
                            CashPoolWithdrawalRequest(
                                amountInCents = 7000,
                                withdrawnAt = Instant.parse("2026-04-03T12:00:00Z"),
                            ),
                        ).header(HttpHeaders.AUTHORIZATION, bearer(ownerEmail)),
                    String::class.java,
                )

            assertEquals(HttpStatus.NO_CONTENT, response.status)
            assertEquals(
                CashPoolBalanceResponse(group = groupId, availableAmountInCents = 3000),
                getCashPoolBalance(groupId, ownerEmail),
            )
            assertEquals(
                GroupMemberCashPoolSharesResponse(
                    group = groupId,
                    shares =
                        listOf(
                            MemberCashPoolShareResponse(coOwnerEmail, 4000),
                        ),
                ),
                getMemberCashPoolShares(groupId, ownerEmail),
            )
            assertEquals(
                GroupBalancesResponse(
                    group = groupId,
                    balances =
                        listOf(
                            GroupMemberBalanceResponse(coOwnerEmail, 1000),
                            GroupMemberBalanceResponse(ownerEmail, -1000),
                        ),
                ),
                getGroupBalances(groupId, ownerEmail),
            )
        }

        @Test
        fun `a withdrawal above the available cash-pool balance is rejected without being persisted`() {
            val ownerEmail = "ledger-over-withdrawal-owner@example.com"
            val coOwnerEmail = "ledger-over-withdrawal-co-owner@example.com"
            val groupId = persistGroup(seed = "ledger-over-withdrawal", ownerEmail = ownerEmail, coOwnerEmail = coOwnerEmail)
            appendCashPoolIncome(groupId = groupId, ownerEmail = ownerEmail, coOwnerEmail = coOwnerEmail)

            val exception =
                assertThrows<HttpClientResponseException> {
                    httpClient.toBlocking().exchange(
                        HttpRequest
                            .POST(
                                "/api/groups/$groupId/cash-pool-withdrawals",
                                CashPoolWithdrawalRequest(
                                    amountInCents = 10_001,
                                    withdrawnAt = Instant.parse("2026-04-03T12:00:00Z"),
                                ),
                            ).header(HttpHeaders.AUTHORIZATION, bearer(ownerEmail)),
                        String::class.java,
                    )
                }

            assertEquals(HttpStatus.CONFLICT, exception.status)
            assertEquals(CashPoolBalanceResponse(groupId, 10_000), getCashPoolBalance(groupId, ownerEmail))
        }
    }

    @Nested
    inner class GetMemberDebtReductionHistory {
        @Test
        fun `a group member can read every operation that reduced another member's debt`() {
            val ownerEmail = "ledger-history-owner@example.com"
            val debtorEmail = "ledger-history-debtor@example.com"
            val groupId = persistGroup(seed = "ledger-history", ownerEmail = ownerEmail, coOwnerEmail = debtorEmail)
            val reimbursementId = testUuid("ledger-history-reimbursement")
            appendDebtReductionHistory(groupId, ownerEmail, debtorEmail, reimbursementId)

            val response =
                httpClient.toBlocking().retrieve(
                    HttpRequest
                        .GET<Any>("/api/groups/$groupId/members/$debtorEmail/debt-reductions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerEmail)),
                    MemberDebtReductionHistoryResponse::class.java,
                )

            assertEquals(groupId, response.group)
            assertEquals(debtorEmail, response.member)
            assertEquals(
                listOf(
                    MemberDebtReductionResponse(
                        type = MemberDebtReductionTypeResponse.CASH_POOL_WITHDRAWAL,
                        operationId = ledgerEventId("ledger-history-withdrawal").toPrimitive(),
                        amountInCents = 4000,
                        occurredAt = Instant.parse("2026-04-03T12:00:00Z"),
                    ),
                    MemberDebtReductionResponse(
                        type = MemberDebtReductionTypeResponse.COMPENSATING_EXPENSE,
                        operationId = expenseId("history-compensation").toPrimitive(),
                        amountInCents = 3000,
                        occurredAt = Instant.parse("2026-04-03T11:00:00Z"),
                    ),
                    MemberDebtReductionResponse(
                        type = MemberDebtReductionTypeResponse.DIRECT_REIMBURSEMENT,
                        operationId = reimbursementId,
                        amountInCents = 2000,
                        occurredAt = Instant.parse("2026-04-03T10:00:00Z"),
                    ),
                ),
                response.reductions,
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
            ledgerEventRepository.append(cashPoolIncome(groupId, ownerEmail, coOwnerEmail))
            ledgerEventRepository.append(
                CashPoolWithdrawalLedgerEvent(
                    id = ledgerEventId("ledger-controller-withdrawal-$groupId"),
                    group = GroupId(groupId),
                    withdrawnBy = MemberEmail.of(ownerEmail),
                    withdrawnAmount = MoneyAmount.ofCents(2500),
                    ownRevenueShareConsumed = MoneyAmount.ofCents(2500),
                    balanceTransfers = emptySet(),
                    occurredAt = Instant.parse("2026-04-03T12:00:00Z"),
                ),
            )
        }
    }

    private fun appendCashPoolIncome(
        groupId: UUID,
        ownerEmail: String,
        coOwnerEmail: String,
    ) {
        runTest {
            ledgerEventRepository.append(cashPoolIncome(groupId, ownerEmail, coOwnerEmail))
        }
    }

    private fun cashPoolIncome(
        groupId: UUID,
        ownerEmail: String,
        coOwnerEmail: String,
    ): CashPoolIncomeLedgerEvent =
        CashPoolIncomeLedgerEvent.from(
            id = ledgerEventId("ledger-controller-income-$groupId"),
            group = GroupId(groupId),
            occurredAt = Instant.parse("2026-04-03T10:00:00Z"),
            distribution =
                RevenueDistribution.distribute(
                    totalAmount = MoneyAmount.ofCents(10_000),
                    ownershipShares =
                        setOf(
                            OwnershipShare(MemberEmail.of(ownerEmail), OwnershipPercentage.ofBasisPoints(6000)),
                            OwnershipShare(MemberEmail.of(coOwnerEmail), OwnershipPercentage.ofBasisPoints(4000)),
                        ),
                ),
        )

    private fun appendDebtReductionHistory(
        groupId: UUID,
        ownerEmail: String,
        debtorEmail: String,
        reimbursementId: UUID,
    ) {
        runTest {
            val group = GroupId(groupId)
            val owner = MemberEmail.of(ownerEmail)
            val debtor = MemberEmail.of(debtorEmail)
            val reimbursement =
                Reimbursement.recordDirect(
                    id = ReimbursementId(reimbursementId),
                    group = group,
                    paidBy = debtor,
                    receivedBy = owner,
                    amount = MoneyAmount.ofCents(2000),
                    reimbursedAt = Instant.parse("2026-04-03T09:30:00Z"),
                    declaredBy = owner,
                    declaredAt = Instant.parse("2026-04-03T10:00:00Z"),
                )
            reimbursementRepository.persist(reimbursement)

            listOf(
                AcceptedExpenseLedgerEvent(
                    id = acceptedExpenseLedgerEventId("history-debt"),
                    group = group,
                    expense = expenseId("history-debt"),
                    paidBy = owner,
                    occurredAt = Instant.parse("2026-04-03T09:00:00Z"),
                    transfers = setOf(MemberBalanceTransfer(debtor, owner, MoneyAmount.ofCents(10_000))),
                ),
                AcceptedReimbursementLedgerEvent.from(reimbursement),
                AcceptedExpenseLedgerEvent(
                    id = acceptedExpenseLedgerEventId("history-compensation"),
                    group = group,
                    expense = expenseId("history-compensation"),
                    paidBy = debtor,
                    occurredAt = Instant.parse("2026-04-03T11:00:00Z"),
                    transfers = setOf(MemberBalanceTransfer(owner, debtor, MoneyAmount.ofCents(3000))),
                ),
                CashPoolWithdrawalLedgerEvent(
                    id = ledgerEventId("ledger-history-withdrawal"),
                    group = group,
                    withdrawnBy = owner,
                    withdrawnAmount = MoneyAmount.ofCents(4000),
                    ownRevenueShareConsumed = MoneyAmount.ZERO,
                    balanceTransfers = setOf(MemberBalanceTransfer(owner, debtor, MoneyAmount.ofCents(4000))),
                    occurredAt = Instant.parse("2026-04-03T12:00:00Z"),
                ),
            ).forEach { event -> ledgerEventRepository.append(event) }
        }
    }

    private fun getCashPoolBalance(
        groupId: UUID,
        requestedBy: String,
    ): CashPoolBalanceResponse =
        httpClient.toBlocking().retrieve(
            HttpRequest
                .GET<Any>("/api/groups/$groupId/cash-pool/balance")
                .header(HttpHeaders.AUTHORIZATION, bearer(requestedBy)),
            CashPoolBalanceResponse::class.java,
        )

    private fun getMemberCashPoolShares(
        groupId: UUID,
        requestedBy: String,
    ): GroupMemberCashPoolSharesResponse =
        httpClient.toBlocking().retrieve(
            HttpRequest
                .GET<Any>("/api/groups/$groupId/cash-pool/shares")
                .header(HttpHeaders.AUTHORIZATION, bearer(requestedBy)),
            GroupMemberCashPoolSharesResponse::class.java,
        )

    private fun getGroupBalances(
        groupId: UUID,
        requestedBy: String,
    ): GroupBalancesResponse =
        httpClient.toBlocking().retrieve(
            HttpRequest
                .GET<Any>("/api/groups/$groupId/balances")
                .header(HttpHeaders.AUTHORIZATION, bearer(requestedBy)),
            GroupBalancesResponse::class.java,
        )

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
