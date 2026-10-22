package tech.justdev.interfaces.ledger

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tech.justdev.application.auth.AuthenticatedUser
import tech.justdev.application.auth.AuthenticatedUserProvider
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.application.ledger.CashPoolHistorySnapshot
import tech.justdev.application.ledger.CashPoolIncomeAllocationSnapshot
import tech.justdev.application.ledger.CashPoolIncomeSnapshot
import tech.justdev.application.ledger.CashPoolWithdrawalSnapshot
import tech.justdev.application.ledger.GetCashPoolBalanceUseCase
import tech.justdev.application.ledger.GetCashPoolHistoryQuery
import tech.justdev.application.ledger.GetCashPoolHistoryUseCase
import tech.justdev.application.ledger.GetGroupBalancesUseCase
import tech.justdev.application.ledger.GetMemberCashPoolSharesUseCase
import tech.justdev.application.ledger.GetMemberDebtReductionHistoryQuery
import tech.justdev.application.ledger.GetMemberDebtReductionHistoryUseCase
import tech.justdev.application.ledger.WithdrawFromCashPoolCommand
import tech.justdev.application.ledger.WithdrawFromCashPoolUseCase
import tech.justdev.application.support.InMemoryGroupRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.ledger.event.LedgerEvent
import tech.justdev.domain.ledger.projection.MemberDebtReduction
import tech.justdev.domain.ledger.repository.LedgerEventRepository
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.testsupport.testUuid
import java.time.Instant

class LedgerControllerTest {
    private val withdrawalUseCase = WithdrawFromCashPoolUseCaseFake()
    private val debtReductionHistoryUseCase = GetMemberDebtReductionHistoryUseCaseFake()
    private val cashPoolHistoryUseCase = GetCashPoolHistoryUseCaseFake()
    private val controller = controller(withdrawalUseCase, debtReductionHistoryUseCase, cashPoolHistoryUseCase)

    @Nested
    inner class GetCashPoolHistory {
        @Test
        fun `should return income distributions and withdrawals on behalf of the authenticated member`() =
            runTest {
                val group = testUuid("cash-pool-history-group")
                val incomeId = testUuid("cash-pool-history-income")
                val withdrawalId = testUuid("cash-pool-history-withdrawal")
                cashPoolHistoryUseCase.result =
                    CashPoolHistorySnapshot(
                        group = group,
                        operations =
                            listOf(
                                CashPoolIncomeSnapshot(
                                    operation = incomeId,
                                    occurredAt = Instant.parse("2026-10-01T10:00:00Z"),
                                    amountInCents = 10_000,
                                    allocations = listOf(CashPoolIncomeAllocationSnapshot("member@example.com", 10_000)),
                                ),
                                CashPoolWithdrawalSnapshot(
                                    operation = withdrawalId,
                                    occurredAt = Instant.parse("2026-10-02T10:00:00Z"),
                                    withdrawnBy = "member@example.com",
                                    amountInCents = 7_000,
                                    ownRevenueShareConsumedInCents = 6_000,
                                ),
                            ),
                    )

                val response = controller.getCashPoolHistory(group)

                assertEquals(
                    GetCashPoolHistoryQuery(
                        group = GroupId(group),
                        requestedBy = MemberEmail.of("member@example.com"),
                    ),
                    cashPoolHistoryUseCase.query,
                )
                assertEquals(
                    CashPoolHistoryResponse(
                        group = group,
                        operations =
                            listOf(
                                CashPoolIncomeResponse(
                                    operation = incomeId,
                                    occurredAt = Instant.parse("2026-10-01T10:00:00Z"),
                                    amountInCents = 10_000,
                                    allocations = listOf(CashPoolIncomeAllocationResponse("member@example.com", 10_000)),
                                ),
                                CashPoolWithdrawalResponse(
                                    operation = withdrawalId,
                                    occurredAt = Instant.parse("2026-10-02T10:00:00Z"),
                                    withdrawnBy = "member@example.com",
                                    amountInCents = 7_000,
                                    ownRevenueShareConsumedInCents = 6_000,
                                ),
                            ),
                    ),
                    response,
                )
            }
    }

    @Nested
    inner class GetMemberDebtReductionHistory {
        @Test
        fun `should return the requested member history on behalf of the authenticated member`() =
            runTest {
                val group = testUuid("debt-reduction-history-group")

                val response = controller.getMemberDebtReductionHistory(group, "target@example.com")

                assertEquals(
                    GetMemberDebtReductionHistoryQuery(
                        group = GroupId(group),
                        member = MemberEmail.of("target@example.com"),
                        requestedBy = MemberEmail.of("member@example.com"),
                    ),
                    debtReductionHistoryUseCase.query,
                )
                assertEquals(
                    MemberDebtReductionHistoryResponse(
                        group = group,
                        member = "target@example.com",
                        reductions = emptyList(),
                    ),
                    response,
                )
            }
    }

    @Nested
    inner class WithdrawFromCashPool {
        @Test
        fun `should withdraw on behalf of the authenticated member`() =
            runTest {
                val group = testUuid("cash-pool-withdrawal-group")

                controller.withdrawFromCashPool(
                    group,
                    CashPoolWithdrawalRequest(
                        amountInCents = 1_250,
                        withdrawnAt = Instant.parse("2026-04-03T12:00:00Z"),
                    ),
                )

                assertEquals(
                    WithdrawFromCashPoolCommand(
                        group = GroupId(group),
                        withdrawnBy = MemberEmail.of("member@example.com"),
                        amount = MoneyAmount.ofCents(1_250),
                        occurredAt = Instant.parse("2026-04-03T12:00:00Z"),
                    ),
                    withdrawalUseCase.command,
                )
            }
    }

    private fun controller(
        withdrawalUseCase: WithdrawFromCashPoolUseCase,
        debtReductionHistoryUseCase: GetMemberDebtReductionHistoryUseCase,
        cashPoolHistoryUseCase: GetCashPoolHistoryUseCase,
    ): LedgerController {
        val groupAccessPolicy = GroupAccessPolicy(InMemoryGroupRepository())
        val ledgerEventRepository = EmptyLedgerEventRepository()
        return LedgerController(
            authenticatedUserProvider = AuthenticatedUserProviderFake(),
            getGroupBalancesUseCase = GetGroupBalancesUseCase(groupAccessPolicy, ledgerEventRepository),
            getCashPoolBalanceUseCase = GetCashPoolBalanceUseCase(groupAccessPolicy, ledgerEventRepository),
            getMemberCashPoolSharesUseCase = GetMemberCashPoolSharesUseCase(groupAccessPolicy, ledgerEventRepository),
            withdrawFromCashPoolUseCase = withdrawalUseCase,
            getMemberDebtReductionHistoryUseCase = debtReductionHistoryUseCase,
            getCashPoolHistoryUseCase = cashPoolHistoryUseCase,
        )
    }

    private class AuthenticatedUserProviderFake : AuthenticatedUserProvider {
        override suspend fun currentAuthenticatedUser(): AuthenticatedUser = AuthenticatedUser(MemberEmail.of("member@example.com"))
    }

    private class WithdrawFromCashPoolUseCaseFake : WithdrawFromCashPoolUseCase {
        var command: WithdrawFromCashPoolCommand? = null

        override suspend fun invoke(command: WithdrawFromCashPoolCommand) {
            this.command = command
        }
    }

    private class GetMemberDebtReductionHistoryUseCaseFake : GetMemberDebtReductionHistoryUseCase {
        var query: GetMemberDebtReductionHistoryQuery? = null

        override suspend fun invoke(query: GetMemberDebtReductionHistoryQuery): List<MemberDebtReduction> {
            this.query = query
            return emptyList()
        }
    }

    private class GetCashPoolHistoryUseCaseFake : GetCashPoolHistoryUseCase {
        var query: GetCashPoolHistoryQuery? = null
        lateinit var result: CashPoolHistorySnapshot

        override suspend fun invoke(query: GetCashPoolHistoryQuery): CashPoolHistorySnapshot {
            this.query = query
            return result
        }
    }

    private class EmptyLedgerEventRepository : LedgerEventRepository {
        override suspend fun append(event: LedgerEvent) = error("not used")

        override suspend fun findByGroup(group: GroupId): List<LedgerEvent> = emptyList()
    }
}
