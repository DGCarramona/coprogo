package tech.justdev.application.ledger

import jakarta.inject.Singleton
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.ledger.projection.projectMemberBalances
import tech.justdev.domain.ledger.repository.LedgerEventRepository
import tech.justdev.domain.shared.valueobject.GroupId
import java.util.UUID

data class GetGroupBalancesQuery(
    val group: GroupId,
    val requestedBy: MemberEmail,
)

data class GroupMemberBalanceSnapshot(
    val member: String,
    val netAmountInCents: Long,
)

data class GroupBalancesSnapshot(
    val group: GroupId,
    val balances: List<GroupMemberBalanceSnapshot>,
)

@Singleton
class GetGroupBalancesUseCase(
    private val groupAccessPolicy: GroupAccessPolicy,
    private val ledgerEventRepository: LedgerEventRepository,
) {
    suspend operator fun invoke(query: GetGroupBalancesQuery): GroupBalancesSnapshot {
        groupAccessPolicy.requireMember(query.group, query.requestedBy)

        return GroupBalancesSnapshot(
            group = query.group,
            balances =
                ledgerEventRepository
                    .findByGroup(query.group)
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
}
