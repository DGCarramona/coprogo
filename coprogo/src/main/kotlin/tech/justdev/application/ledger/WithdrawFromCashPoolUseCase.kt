package tech.justdev.application.ledger

import jakarta.inject.Singleton
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.ledger.event.CashPoolWithdrawalLedgerEvent
import tech.justdev.domain.ledger.projection.projectCashPoolBalance
import tech.justdev.domain.ledger.projection.projectMemberCashPoolShares
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import java.time.Instant

data class WithdrawFromCashPoolCommand(
    val group: GroupId,
    val withdrawnBy: MemberEmail,
    val amount: MoneyAmount,
    val occurredAt: Instant,
)

interface WithdrawFromCashPoolUseCase {
    suspend operator fun invoke(command: WithdrawFromCashPoolCommand)
}

@Singleton
class WithdrawFromCashPoolUseCaseImpl(
    private val groupAccessPolicy: GroupAccessPolicy,
    private val cashPoolWithdrawalPersistence: CashPoolWithdrawalPersistence,
    private val ledgerEventIdGenerator: LedgerEventIdGenerator,
) : WithdrawFromCashPoolUseCase {
    override suspend fun invoke(command: WithdrawFromCashPoolCommand) {
        groupAccessPolicy.requireMember(command.group, command.withdrawnBy)

        cashPoolWithdrawalPersistence.appendFromCurrentLedger(command.group) { events ->
            val availableAmount = MoneyAmount.ofCents(events.projectCashPoolBalance().inCents().coerceAtLeast(0))
            if (command.amount > availableAmount) {
                throw InsufficientCashPoolBalanceException(
                    requestedAmount = command.amount,
                    availableAmount = availableAmount,
                )
            }

            CashPoolWithdrawalLedgerEvent.from(
                id = ledgerEventIdGenerator.next(),
                group = command.group,
                withdrawnBy = command.withdrawnBy,
                withdrawnAmount = command.amount,
                occurredAt = command.occurredAt,
                memberCashPoolShares = events.projectMemberCashPoolShares(),
            )
        }
    }
}
