package tech.justdev.application.expense

import jakarta.inject.Singleton
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.application.shared.TransactionRunner
import tech.justdev.domain.expense.repository.ExpenseRepository
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.expense.valueobject.ExpenseParticipationDecision
import tech.justdev.domain.expense.valueobject.ExpenseStatus
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.ledger.event.AcceptedExpenseLedgerEvent
import tech.justdev.domain.ledger.repository.LedgerEventRepository
import tech.justdev.domain.shared.valueobject.GroupId
import java.time.Instant
import java.util.UUID

enum class ExpenseParticipationDecisionCommand {
    APPROVE,
    REFUSE,
}

data class RecordExpenseParticipationDecisionCommand(
    val group: GroupId,
    val id: UUID,
    val member: MemberEmail,
    val decision: ExpenseParticipationDecisionCommand,
    val decidedAt: Instant,
)

interface RecordExpenseParticipationDecisionUseCase {
    suspend operator fun invoke(command: RecordExpenseParticipationDecisionCommand)
}

@Singleton
class RecordExpenseParticipationDecisionUseCaseImpl(
    private val expenseRepository: ExpenseRepository,
    private val ledgerEventRepository: LedgerEventRepository,
    private val groupAccessPolicy: GroupAccessPolicy,
    private val transactionRunner: TransactionRunner,
) : RecordExpenseParticipationDecisionUseCase {
    override suspend operator fun invoke(command: RecordExpenseParticipationDecisionCommand) {
        groupAccessPolicy.requireMember(command.group, command.member)

        transactionRunner.transaction {
            val existingExpense =
                expenseRepository
                    .findProposedByIdAndGroup(ExpenseId(command.id), command.group)
                    ?: throw IllegalArgumentException("expense ${command.id} was not found")

            val updatedExpense =
                existingExpense.recordParticipationDecision(
                    member = command.member,
                    decision =
                        when (command.decision) {
                            ExpenseParticipationDecisionCommand.APPROVE -> ExpenseParticipationDecision.APPROVE
                            ExpenseParticipationDecisionCommand.REFUSE -> ExpenseParticipationDecision.REFUSE
                        },
                    decidedAt = command.decidedAt,
                )

            expenseRepository.persist(updatedExpense)
            if (updatedExpense.status !== ExpenseStatus.ACCEPTED) return@transaction
            AcceptedExpenseLedgerEvent.from(updatedExpense)?.let { ledgerEventRepository.append(it) }
        }
    }
}
