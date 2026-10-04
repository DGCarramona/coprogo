package tech.justdev.application.reimbursement

import jakarta.inject.Singleton
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.reimbursement.entity.Reimbursement
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import java.time.Instant

data class RecordDirectReimbursementCommand(
    val group: GroupId,
    val paidBy: MemberEmail,
    val recordedBy: MemberEmail,
    val amountInCents: Long,
    val reimbursedAt: Instant,
    val recordedAt: Instant,
)

interface RecordDirectReimbursementUseCase {
    suspend operator fun invoke(command: RecordDirectReimbursementCommand)
}

@Singleton
class RecordDirectReimbursementUseCaseImpl(
    private val groupAccessPolicy: GroupAccessPolicy,
    private val reimbursementIdGenerator: ReimbursementIdGenerator,
    private val acceptedReimbursementPersistence: AcceptedReimbursementPersistence,
) : RecordDirectReimbursementUseCase {
    override suspend operator fun invoke(command: RecordDirectReimbursementCommand) {
        val group = groupAccessPolicy.requireMember(command.group, command.recordedBy)
        require(group.contains(command.paidBy)) {
            "reimbursement payer ${command.paidBy.toPrimitive()} is not part of group ${command.group.toPrimitive()}"
        }

        Reimbursement
            .recordDirect(
                id = reimbursementIdGenerator.next(),
                group = command.group,
                paidBy = command.paidBy,
                receivedBy = command.recordedBy,
                amount = MoneyAmount.ofCents(command.amountInCents),
                reimbursedAt = command.reimbursedAt,
                declaredBy = command.recordedBy,
                declaredAt = command.recordedAt,
            ).let { reimbursement -> acceptedReimbursementPersistence.persist(reimbursement) }
    }
}
