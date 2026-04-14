package tech.justdev.application.expense

import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.repository.ExpenseRepository
import tech.justdev.domain.expense.valueobject.ExpenseShare
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import java.time.Instant

sealed interface ExpenseAllocationCommand

data class EqualSplitExpenseAllocationCommand(
    val participants: Set<MemberEmail>,
) : ExpenseAllocationCommand

data class FixedExpenseAllocationCommand(
    val participations: Set<FixedExpenseParticipationCommand>,
) : ExpenseAllocationCommand

data class FixedExpenseParticipationCommand(
    val member: MemberEmail,
    val amountInCents: Long,
)

data class ProposeExpenseCommand(
    val group: GroupId,
    val title: String,
    val createdBy: MemberEmail,
    val totalAmountInCents: Long,
    val createdAt: Instant,
    val allocation: ExpenseAllocationCommand,
)

class ProposeExpenseUseCase(
    private val expenseRepository: ExpenseRepository,
    private val expenseIdGenerator: ExpenseIdGenerator = RandomExpenseIdGenerator,
) {
    suspend operator fun invoke(command: ProposeExpenseCommand) {
        expenseRepository.persist(
            when (val allocation = command.allocation) {
                is EqualSplitExpenseAllocationCommand -> {
                    Expense.proposeEqualSplit(
                        id = expenseIdGenerator.next(),
                        group = command.group,
                        title = command.title,
                        createdBy = command.createdBy,
                        totalAmount = MoneyAmount.ofCents(command.totalAmountInCents),
                        createdAt = command.createdAt,
                        participants = allocation.participants,
                    )
                }

                is FixedExpenseAllocationCommand -> {
                    Expense.propose(
                        id = expenseIdGenerator.next(),
                        group = command.group,
                        title = command.title,
                        createdBy = command.createdBy,
                        totalAmount = MoneyAmount.ofCents(command.totalAmountInCents),
                        createdAt = command.createdAt,
                        shares =
                            allocation.participations
                                .map { participation ->
                                    ExpenseShare(
                                        member = participation.member,
                                        amount = MoneyAmount.ofCents(participation.amountInCents),
                                    )
                                }.toSet(),
                    )
                }
            },
        )
    }
}
