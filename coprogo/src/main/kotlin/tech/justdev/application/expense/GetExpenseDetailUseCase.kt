package tech.justdev.application.expense

import jakarta.inject.Singleton
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.repository.ExpenseRepository
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.expense.valueobject.ExpenseParticipationStatus
import tech.justdev.domain.expense.valueobject.ExpenseStatus
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import java.time.Instant

data class GetExpenseDetailQuery(
    val group: GroupId,
    val id: ExpenseId,
    val requestedBy: MemberEmail,
)

data class ExpenseDetailParticipationSnapshot(
    val member: MemberEmail,
    val amount: MoneyAmount,
    val status: ExpenseParticipationStatus,
)

data class ExpenseDetailSnapshot(
    val id: ExpenseId,
    val title: String,
    val createdBy: MemberEmail,
    val totalAmount: MoneyAmount,
    val createdAt: Instant,
    val status: ExpenseStatus,
    val participations: List<ExpenseDetailParticipationSnapshot>,
    val refusal: ExpenseRefusalSnapshot? = null,
)

class ExpenseNotFoundException(
    val id: ExpenseId,
    val group: GroupId,
) : RuntimeException("expense ${id.toPrimitive()} was not found in group ${group.toPrimitive()}")

interface GetExpenseDetailUseCase {
    suspend operator fun invoke(query: GetExpenseDetailQuery): ExpenseDetailSnapshot
}

@Singleton
class GetExpenseDetailUseCaseImpl(
    private val expenseRepository: ExpenseRepository,
    private val groupAccessPolicy: GroupAccessPolicy,
) : GetExpenseDetailUseCase {
    override suspend operator fun invoke(query: GetExpenseDetailQuery): ExpenseDetailSnapshot {
        groupAccessPolicy.requireMember(query.group, query.requestedBy)

        val expense =
            expenseRepository.findByIdAndGroup(query.id, query.group)
                ?: throw ExpenseNotFoundException(query.id, query.group)

        return expense.toDetailSnapshot()
    }
}

internal fun Expense.toDetailSnapshot(): ExpenseDetailSnapshot =
    ExpenseDetailSnapshot(
        id = id,
        title = title,
        createdBy = createdBy,
        totalAmount = totalAmount,
        createdAt = createdAt,
        status = status,
        refusal = toRefusalSnapshot(),
        participations =
            participations
                .sortedBy { participation -> participation.member.toPrimitive() }
                .map { participation ->
                    ExpenseDetailParticipationSnapshot(
                        member = participation.member,
                        amount = participation.amount,
                        status = participation.status,
                    )
                },
    )
