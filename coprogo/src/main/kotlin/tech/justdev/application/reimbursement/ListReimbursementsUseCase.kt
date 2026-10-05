package tech.justdev.application.reimbursement

import jakarta.inject.Singleton
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.reimbursement.entity.Reimbursement
import tech.justdev.domain.reimbursement.repository.ReimbursementRepository
import tech.justdev.domain.shared.valueobject.GroupId

data class ListReimbursementsQuery(
    val group: GroupId,
    val requestedBy: MemberEmail,
)

interface ListReimbursementsUseCase {
    suspend operator fun invoke(query: ListReimbursementsQuery): List<Reimbursement>
}

@Singleton
class ListReimbursementsUseCaseImpl(
    private val groupAccessPolicy: GroupAccessPolicy,
    private val reimbursementRepository: ReimbursementRepository,
) : ListReimbursementsUseCase {
    override suspend fun invoke(query: ListReimbursementsQuery): List<Reimbursement> {
        groupAccessPolicy.requireMember(query.group, query.requestedBy)

        return reimbursementRepository
            .findByGroup(query.group)
            .sortedWith(
                compareByDescending<Reimbursement> { reimbursement -> reimbursement.reimbursedAt }
                    .thenByDescending { reimbursement -> reimbursement.id.toPrimitive() },
            )
    }
}
