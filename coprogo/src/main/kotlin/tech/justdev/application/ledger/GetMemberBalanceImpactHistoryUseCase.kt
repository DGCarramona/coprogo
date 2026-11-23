package tech.justdev.application.ledger

import jakarta.inject.Singleton
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.ledger.projection.MemberBalanceImpact
import tech.justdev.domain.ledger.projection.projectMemberBalanceImpacts
import tech.justdev.domain.ledger.projection.replayCashPoolChronologically
import tech.justdev.domain.ledger.repository.LedgerEventRepository
import tech.justdev.domain.shared.valueobject.GroupId

data class GetMemberBalanceImpactHistoryQuery(
    val group: GroupId,
    val member: MemberEmail,
    val requestedBy: MemberEmail,
)

interface GetMemberBalanceImpactHistoryUseCase {
    suspend operator fun invoke(query: GetMemberBalanceImpactHistoryQuery): List<MemberBalanceImpact>
}

@Singleton
class GetMemberBalanceImpactHistoryUseCaseImpl(
    private val groupAccessPolicy: GroupAccessPolicy,
    private val ledgerEventRepository: LedgerEventRepository,
) : GetMemberBalanceImpactHistoryUseCase {
    override suspend fun invoke(query: GetMemberBalanceImpactHistoryQuery): List<MemberBalanceImpact> {
        val group = groupAccessPolicy.requireMember(query.group, query.requestedBy)
        require(group.contains(query.member)) {
            "balance impact history member ${query.member.toPrimitive()} is not part of group ${query.group.toPrimitive()}"
        }

        return ledgerEventRepository
            .findByGroup(query.group)
            .replayCashPoolChronologically()
            .projectMemberBalanceImpacts(query.member)
            .sortedWith(
                compareByDescending<MemberBalanceImpact> { impact -> impact.event.occurredAt }
                    .thenByDescending { impact -> impact.event.id.toPrimitive() },
            )
    }
}
