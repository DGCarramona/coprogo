package tech.justdev.interfaces.ledger

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tech.justdev.application.auth.AuthenticatedUser
import tech.justdev.application.auth.AuthenticatedUserProvider
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.application.ledger.GetCashPoolBalanceUseCase
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
    private val controller = controller(withdrawalUseCase, debtReductionHistoryUseCase)

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

    private class EmptyLedgerEventRepository : LedgerEventRepository {
        override suspend fun append(event: LedgerEvent) = error("not used")

        override suspend fun findByGroup(group: GroupId): List<LedgerEvent> = emptyList()
    }
}
