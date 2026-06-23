package tech.justdev.application.ledger

import jakarta.inject.Singleton
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.ledger.projection.projectCashPoolBalance
import tech.justdev.domain.ledger.repository.LedgerEventRepository
import tech.justdev.domain.shared.valueobject.GroupId
import java.util.UUID

data class GetCashPoolBalanceQuery(
    val group: GroupId,
    val requestedBy: MemberEmail,
)

data class CashPoolBalanceSnapshot(
    val group: GroupId,
    val availableAmountInCents: Long,
)

@Singleton
class GetCashPoolBalanceUseCase(
    private val groupAccessPolicy: GroupAccessPolicy,
    private val ledgerEventRepository: LedgerEventRepository,
) {
    suspend operator fun invoke(query: GetCashPoolBalanceQuery): CashPoolBalanceSnapshot {
        groupAccessPolicy.requireMember(query.group, query.requestedBy)

        return CashPoolBalanceSnapshot(
            group = query.group,
            availableAmountInCents =
                ledgerEventRepository
                    .findByGroup(query.group)
                    .projectCashPoolBalance()
                    .inCents(),
        )
    }
}
