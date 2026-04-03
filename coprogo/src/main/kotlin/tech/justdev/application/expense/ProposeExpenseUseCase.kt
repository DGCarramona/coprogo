package tech.justdev.application.expense

import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.repository.ExpenseRepository
import tech.justdev.domain.expense.valueobject.ExpenseShare
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.domain.shared.valueobject.MemberId
import tech.justdev.domain.shared.money.MoneyAmount
import java.time.Instant
import java.util.UUID

sealed interface ExpenseAllocationCommand

data class EqualSplitExpenseAllocationCommand(
    val participants: Set<UUID>,
) : ExpenseAllocationCommand

data class FixedExpenseAllocationCommand(
    val participations: Set<FixedExpenseParticipationCommand>,
) : ExpenseAllocationCommand

data class FixedExpenseParticipationCommand(
    val member: UUID,
    val amountInCents: Long,
)

data class ProposeExpenseCommand(
    val group: UUID,
    val title: String,
    val createdBy: UUID,
    val totalAmountInCents: Long,
    val createdAt: Instant,
    val allocation: ExpenseAllocationCommand,
)

class ProposeExpenseUseCase(
    private val expenseRepository: ExpenseRepository,
    private val expenseIdGenerator: ExpenseIdGenerator = RandomExpenseIdGenerator,
) {

    operator fun invoke(command: ProposeExpenseCommand) {
        expenseRepository.save(when (val allocation = command.allocation) {
            is EqualSplitExpenseAllocationCommand -> Expense.proposeEqualSplit(
                id = expenseIdGenerator.next(),
                group = GroupId(command.group),
                title = command.title,
                createdBy = MemberId(command.createdBy),
                totalAmount = MoneyAmount.ofCents(command.totalAmountInCents),
                createdAt = command.createdAt,
                participants = allocation.participants.map(::MemberId).toSet(),
            )

            is FixedExpenseAllocationCommand -> Expense.propose(
                id = expenseIdGenerator.next(),
                group = GroupId(command.group),
                title = command.title,
                createdBy = MemberId(command.createdBy),
                totalAmount = MoneyAmount.ofCents(command.totalAmountInCents),
                createdAt = command.createdAt,
                shares = allocation.participations.map { participation ->
                    ExpenseShare(
                        member = MemberId(participation.member),
                        amount = MoneyAmount.ofCents(participation.amountInCents),
                    )
                }.toSet(),
            )
        })
    }
}
