package tech.justdev.application.ledger

import jakarta.inject.Singleton
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.ledger.projection.MemberDebtReduction
import tech.justdev.domain.ledger.projection.projectMemberDebtReductions
import tech.justdev.domain.ledger.repository.LedgerEventRepository
import tech.justdev.domain.shared.valueobject.GroupId

data class GetMemberDebtReductionHistoryQuery(
    val group: GroupId,
    val member: MemberEmail,
    val requestedBy: MemberEmail,
)

interface GetMemberDebtReductionHistoryUseCase {
    suspend operator fun invoke(query: GetMemberDebtReductionHistoryQuery): List<MemberDebtReduction>
}

@Singleton
class GetMemberDebtReductionHistoryUseCaseImpl(
    private val groupAccessPolicy: GroupAccessPolicy,
    private val ledgerEventRepository: LedgerEventRepository,
) : GetMemberDebtReductionHistoryUseCase {
    override suspend fun invoke(query: GetMemberDebtReductionHistoryQuery): List<MemberDebtReduction> {
        val group = groupAccessPolicy.requireMember(query.group, query.requestedBy)
        require(group.contains(query.member)) {
            "debt reduction history member ${query.member.toPrimitive()} is not part of group ${query.group.toPrimitive()}"
        }

        return ledgerEventRepository
            .findByGroup(query.group)
            .projectMemberDebtReductions(query.member)
            .sortedWith(
                compareByDescending<MemberDebtReduction> { reduction -> reduction.event.occurredAt }
                    .thenByDescending { reduction -> reduction.event.id.toPrimitive() },
            )
    }
}
