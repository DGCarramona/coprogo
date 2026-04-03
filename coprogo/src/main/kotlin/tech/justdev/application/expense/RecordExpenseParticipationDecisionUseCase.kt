package tech.justdev.application.expense

import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.expense.valueobject.ExpenseParticipationDecision
import tech.justdev.domain.expense.repository.ExpenseRepository
import tech.justdev.domain.expense.valueobject.ExpenseStatus
import tech.justdev.domain.ledger.repository.LedgerEventRepository
import tech.justdev.domain.ledger.event.AcceptedExpenseLedgerEvent
import tech.justdev.domain.shared.valueobject.MemberId
import java.time.Instant
import java.util.UUID

enum class ExpenseParticipationDecisionCommand {
    APPROVE,
    REFUSE,
}

data class RecordExpenseParticipationDecisionCommand(
    val id: UUID,
    val member: UUID,
    val decision: ExpenseParticipationDecisionCommand,
    val decidedAt: Instant,
)

class RecordExpenseParticipationDecisionUseCase(
    private val expenseRepository: ExpenseRepository,
    private val ledgerEventRepository: LedgerEventRepository,
) {

    operator fun invoke(command: RecordExpenseParticipationDecisionCommand) {
        val existingExpense = expenseRepository.findProposedById(ExpenseId(command.id))
            ?: throw IllegalArgumentException("expense ${command.id} was not found")

        val updatedExpense = existingExpense.recordParticipationDecision(
            member = MemberId(command.member),
            decision = when (command.decision) {
                ExpenseParticipationDecisionCommand.APPROVE -> ExpenseParticipationDecision.APPROVE
                ExpenseParticipationDecisionCommand.REFUSE -> ExpenseParticipationDecision.REFUSE
            },
            decidedAt = command.decidedAt,
        )

        expenseRepository.save(updatedExpense)
        if (updatedExpense.status !== ExpenseStatus.ACCEPTED) return
        AcceptedExpenseLedgerEvent.from(updatedExpense)?.let { ledgerEventRepository.append(it) }
    }
}
