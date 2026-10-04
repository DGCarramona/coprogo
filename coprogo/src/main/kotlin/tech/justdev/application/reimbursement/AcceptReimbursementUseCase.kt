package tech.justdev.application.reimbursement

import jakarta.inject.Singleton
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.reimbursement.repository.ReimbursementRepository
import tech.justdev.domain.reimbursement.valueobject.ReimbursementId
import tech.justdev.domain.shared.valueobject.GroupId
import java.time.Instant

data class AcceptReimbursementCommand(
    val group: GroupId,
    val reimbursement: ReimbursementId,
    val acceptedBy: MemberEmail,
    val acceptedAt: Instant,
)

interface AcceptReimbursementUseCase {
    suspend operator fun invoke(command: AcceptReimbursementCommand)
}

@Singleton
class AcceptReimbursementUseCaseImpl(
    private val groupAccessPolicy: GroupAccessPolicy,
    private val reimbursementRepository: ReimbursementRepository,
    private val acceptedReimbursementPersistence: AcceptedReimbursementPersistence,
) : AcceptReimbursementUseCase {
    override suspend fun invoke(command: AcceptReimbursementCommand) {
        groupAccessPolicy.requireMember(command.group, command.acceptedBy)

        reimbursementRepository
            .findByIdAndGroup(command.reimbursement, command.group)
            .let { reimbursement ->
                reimbursement ?: throw ReimbursementNotFoundException(command.reimbursement, command.group)
            }.accept(
                reviewedBy = command.acceptedBy,
                acceptedAt = command.acceptedAt,
            ).let { reimbursement -> acceptedReimbursementPersistence.persist(reimbursement) }
    }
}
