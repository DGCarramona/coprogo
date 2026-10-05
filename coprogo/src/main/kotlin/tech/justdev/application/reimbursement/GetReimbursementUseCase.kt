package tech.justdev.application.reimbursement

import jakarta.inject.Singleton
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.reimbursement.entity.Reimbursement
import tech.justdev.domain.reimbursement.repository.ReimbursementRepository
import tech.justdev.domain.reimbursement.valueobject.ReimbursementId
import tech.justdev.domain.shared.valueobject.GroupId

data class GetReimbursementQuery(
    val group: GroupId,
    val reimbursement: ReimbursementId,
    val requestedBy: MemberEmail,
)

interface GetReimbursementUseCase {
    suspend operator fun invoke(query: GetReimbursementQuery): Reimbursement
}

@Singleton
class GetReimbursementUseCaseImpl(
    private val groupAccessPolicy: GroupAccessPolicy,
    private val reimbursementRepository: ReimbursementRepository,
) : GetReimbursementUseCase {
    override suspend fun invoke(query: GetReimbursementQuery): Reimbursement {
        groupAccessPolicy.requireMember(query.group, query.requestedBy)

        return reimbursementRepository
            .findByIdAndGroup(query.reimbursement, query.group)
            ?: throw ReimbursementNotFoundException(query.reimbursement, query.group)
    }
}
