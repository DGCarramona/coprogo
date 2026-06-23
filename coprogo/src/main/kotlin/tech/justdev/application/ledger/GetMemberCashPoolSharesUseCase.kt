package tech.justdev.application.ledger

import jakarta.inject.Singleton
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.ledger.projection.projectMemberCashPoolShares
import tech.justdev.domain.ledger.repository.LedgerEventRepository
import tech.justdev.domain.shared.valueobject.GroupId
import java.util.UUID

data class GetMemberCashPoolSharesQuery(
    val group: GroupId,
    val requestedBy: MemberEmail,
)

data class MemberCashPoolShareSnapshot(
    val member: String,
    val amountInCents: Long,
)

data class GroupMemberCashPoolSharesSnapshot(
    val group: GroupId,
    val shares: List<MemberCashPoolShareSnapshot>,
)

@Singleton
class GetMemberCashPoolSharesUseCase(
    private val groupAccessPolicy: GroupAccessPolicy,
    private val ledgerEventRepository: LedgerEventRepository,
) {
    suspend operator fun invoke(query: GetMemberCashPoolSharesQuery): GroupMemberCashPoolSharesSnapshot {
        groupAccessPolicy.requireMember(query.group, query.requestedBy)

        return GroupMemberCashPoolSharesSnapshot(
            group = query.group,
            shares =
                ledgerEventRepository
                    .findByGroup(query.group)
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
}
