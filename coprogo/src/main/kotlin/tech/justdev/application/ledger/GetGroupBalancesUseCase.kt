package tech.justdev.application.ledger

import tech.justdev.domain.ledger.repository.LedgerEventRepository
import tech.justdev.domain.ledger.projection.projectMemberBalances
import tech.justdev.domain.shared.valueobject.GroupId
import java.util.UUID

data class GetGroupBalancesQuery(
    val group: UUID,
)

data class GroupMemberBalanceSnapshot(
    val member: UUID,
    val netAmountInCents: Long,
)

data class GroupBalancesSnapshot(
    val group: UUID,
    val balances: List<GroupMemberBalanceSnapshot>,
)

class GetGroupBalancesUseCase(
    private val ledgerEventRepository: LedgerEventRepository,
) {

    operator fun invoke(query: GetGroupBalancesQuery): GroupBalancesSnapshot = GroupBalancesSnapshot(
        group = query.group,
        balances = ledgerEventRepository.findByGroup(GroupId(query.group))
            .projectMemberBalances()
            .sortedBy { balance -> balance.member.toPrimitive() }
            .map { balance ->
                GroupMemberBalanceSnapshot(
                    member = balance.member.toPrimitive(),
                    netAmountInCents = balance.netAmount.inCents(),
                )
            },
    )
}
