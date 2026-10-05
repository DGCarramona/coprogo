package tech.justdev.application.reimbursement

import jakarta.inject.Singleton
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.reimbursement.repository.ReimbursementRepository
import tech.justdev.domain.reimbursement.valueobject.ReimbursementId
import tech.justdev.domain.reimbursement.valueobject.ReimbursementRejectionReason
import tech.justdev.domain.shared.valueobject.GroupId
import java.time.Instant

data class RejectReimbursementCommand(
    val group: GroupId,
    val reimbursement: ReimbursementId,
    val rejectedBy: MemberEmail,
    val rejectedAt: Instant,
    val reason: ReimbursementRejectionReason?,
)

class ReimbursementNotFoundException(
    val reimbursement: ReimbursementId,
    val group: GroupId,
) : RuntimeException(
        "reimbursement ${reimbursement.toPrimitive()} was not found in group ${group.toPrimitive()}",
    )

interface RejectReimbursementUseCase {
    suspend operator fun invoke(command: RejectReimbursementCommand)
}

@Singleton
class RejectReimbursementUseCaseImpl(
    private val groupAccessPolicy: GroupAccessPolicy,
    private val reimbursementRepository: ReimbursementRepository,
) : RejectReimbursementUseCase {
    override suspend fun invoke(command: RejectReimbursementCommand) {
        groupAccessPolicy.requireMember(command.group, command.rejectedBy)

        reimbursementRepository
            .findByIdAndGroup(command.reimbursement, command.group)
            .let { reimbursement ->
                reimbursement ?: throw ReimbursementNotFoundException(command.reimbursement, command.group)
            }.reject(
                reviewedBy = command.rejectedBy,
                rejectedAt = command.rejectedAt,
                reason = command.reason,
            ).let { reimbursement -> reimbursementRepository.persist(reimbursement) }
    }
}
