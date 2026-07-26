package tech.justdev.application.expense

import jakarta.inject.Singleton
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.domain.expense.repository.ExpenseRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId
import java.time.Instant
import java.util.UUID

data class ListGroupExpensesQuery(
    val group: GroupId,
    val requestedBy: MemberEmail,
)

data class ExpenseSnapshot(
    val id: UUID,
    val title: String,
    val createdBy: String,
    val totalAmountCents: Long,
    val createdAt: Instant,
    val status: String,
)

interface ListGroupExpensesUseCase {
    suspend operator fun invoke(query: ListGroupExpensesQuery): List<ExpenseSnapshot>
}

@Singleton
class ListGroupExpensesUseCaseImpl(
    private val expenseRepository: ExpenseRepository,
    private val groupAccessPolicy: GroupAccessPolicy,
) : ListGroupExpensesUseCase {
    override suspend operator fun invoke(query: ListGroupExpensesQuery): List<ExpenseSnapshot> {
        groupAccessPolicy.requireMember(query.group, query.requestedBy)

        return expenseRepository
            .findByGroup(query.group)
            .sortedBy { expense -> expense.createdAt }
            .map { expense ->
                ExpenseSnapshot(
                    id = expense.id.toPrimitive(),
                    title = expense.title,
                    createdBy = expense.createdBy.toPrimitive(),
                    totalAmountCents = expense.totalAmount.inCents(),
                    createdAt = expense.createdAt,
                    status = expense.status.name,
                )
            }
    }
}
