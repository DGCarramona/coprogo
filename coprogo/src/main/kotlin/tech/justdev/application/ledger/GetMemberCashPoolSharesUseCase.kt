package tech.justdev.application.ledger

import tech.justdev.domain.ledger.projection.projectMemberCashPoolShares
import tech.justdev.domain.ledger.repository.LedgerEventRepository
import tech.justdev.domain.shared.valueobject.GroupId
import java.util.UUID

data class GetMemberCashPoolSharesQuery(
    val group: UUID,
)

data class MemberCashPoolShareSnapshot(
    val member: UUID,
    val amountInCents: Long,
)

data class GroupMemberCashPoolSharesSnapshot(
    val group: UUID,
    val shares: List<MemberCashPoolShareSnapshot>,
)

class GetMemberCashPoolSharesUseCase(
    private val ledgerEventRepository: LedgerEventRepository,
) {

    operator fun invoke(query: GetMemberCashPoolSharesQuery): GroupMemberCashPoolSharesSnapshot =
        GroupMemberCashPoolSharesSnapshot(
            group = query.group,
            shares = ledgerEventRepository
                .findByGroup(GroupId(query.group))
                .projectMemberCashPoolShares()
                .sortedBy { balance -> balance.member.toPrimitive() }
                .map { balance ->
                    MemberCashPoolShareSnapshot(
                        member = balance.member.toPrimitive(),
                        amountInCents = balance.amount.inCents(),
                    )
                },
        )
}
