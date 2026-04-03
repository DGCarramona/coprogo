package tech.justdev.application.ledger

import tech.justdev.domain.ledger.projection.projectCashPoolBalance
import tech.justdev.domain.ledger.repository.LedgerEventRepository
import tech.justdev.domain.shared.valueobject.GroupId
import java.util.UUID

data class GetCashPoolBalanceQuery(
    val group: UUID,
)

data class CashPoolBalanceSnapshot(
    val group: UUID,
    val availableAmountInCents: Long,
)

class GetCashPoolBalanceUseCase(
    private val ledgerEventRepository: LedgerEventRepository,
) {

    operator fun invoke(query: GetCashPoolBalanceQuery): CashPoolBalanceSnapshot = CashPoolBalanceSnapshot(
        group = query.group,
        availableAmountInCents = ledgerEventRepository
            .findByGroup(GroupId(query.group))
            .projectCashPoolBalance()
            .inCents(),
    )
}
