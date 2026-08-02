package tech.justdev.application.expense

import jakarta.inject.Singleton
import tech.justdev.application.group.GroupAccessPolicy
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

data class CustomExpenseAllocationCommand(
    val participations: Set<CustomExpenseParticipationCommand>,
) : ExpenseAllocationCommand

data class CustomExpenseParticipationCommand(
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

interface ProposeExpenseUseCase {
    suspend operator fun invoke(command: ProposeExpenseCommand)
}

@Singleton
class ProposeExpenseUseCaseImpl(
    private val expenseRepository: ExpenseRepository,
    private val groupAccessPolicy: GroupAccessPolicy,
    private val expenseIdGenerator: ExpenseIdGenerator = RandomExpenseIdGenerator,
) : ProposeExpenseUseCase {
    override suspend operator fun invoke(command: ProposeExpenseCommand) {
        val group = groupAccessPolicy.requireMember(command.group, command.createdBy)
        val nonMember =
            when (val allocation = command.allocation) {
                is EqualSplitExpenseAllocationCommand -> allocation.participants
                is CustomExpenseAllocationCommand -> allocation.participations.map { participation -> participation.member }.toSet()
            }.let {
                it
                    .filterNot(group::contains)
                    .minByOrNull { member -> member.toPrimitive() }
            }
        if (nonMember != null) {
            throw IllegalArgumentException(
                "expense participant ${nonMember.toPrimitive()} is not part of group ${command.group.toPrimitive()}",
            )
        }

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

                is CustomExpenseAllocationCommand -> {
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
