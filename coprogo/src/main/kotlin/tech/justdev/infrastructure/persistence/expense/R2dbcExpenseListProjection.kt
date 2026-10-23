package tech.justdev.infrastructure.persistence.expense

import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.jooq.Record
import tech.justdev.application.expense.ExpenseListProjection
import tech.justdev.application.expense.ExpenseRefusalSnapshot
import tech.justdev.application.expense.ExpenseSnapshot
import tech.justdev.domain.expense.valueobject.RefusalReason
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.infrastructure.persistence.jooq.Tables.EXPENSES
import tech.justdev.infrastructure.persistence.jooq.Tables.EXPENSE_PARTICIPATIONS
import tech.justdev.infrastructure.persistence.jooq.awaitList
import tech.justdev.infrastructure.persistence.jooq.dsl
import tech.justdev.infrastructure.persistence.jooq.enums.ExpenseParticipationStatus

@Singleton
class R2dbcExpenseListProjection(
    @param:Named("default")
    private val connectionFactory: ConnectionFactory,
) : ExpenseListProjection {
    override suspend fun findByGroup(group: GroupId): List<ExpenseSnapshot> =
        connectionFactory
            .dsl()
            .select(
                EXPENSES.ID,
                EXPENSES.TITLE,
                EXPENSES.CREATED_BY,
                EXPENSES.TOTAL_AMOUNT,
                EXPENSES.CREATED_AT,
                EXPENSES.STATUS,
                EXPENSE_PARTICIPATIONS.MEMBER,
                EXPENSE_PARTICIPATIONS.DECIDED_AT,
                EXPENSE_PARTICIPATIONS.REFUSAL_REASON,
            ).from(EXPENSES)
            .leftJoin(EXPENSE_PARTICIPATIONS)
            .on(EXPENSE_PARTICIPATIONS.EXPENSE.eq(EXPENSES.ID))
            .and(EXPENSE_PARTICIPATIONS.STATUS.eq(ExpenseParticipationStatus.REFUSED))
            .where(EXPENSES.GROUP.eq(group.toPrimitive()))
            .orderBy(EXPENSES.CREATED_AT.asc(), EXPENSES.ID.desc())
            .awaitList()
            .map(Record::toExpenseSnapshot)
}

private fun Record.toExpenseSnapshot(): ExpenseSnapshot =
    ExpenseSnapshot(
        id = requireNotNull(get(EXPENSES.ID)),
        title = requireNotNull(get(EXPENSES.TITLE)),
        createdBy = requireNotNull(get(EXPENSES.CREATED_BY)),
        totalAmountCents = requireNotNull(get(EXPENSES.TOTAL_AMOUNT)),
        createdAt = requireNotNull(get(EXPENSES.CREATED_AT)).toInstant(),
        status = requireNotNull(get(EXPENSES.STATUS)).literal,
        refusal = toRefusalSnapshot(),
    )

private fun Record.toRefusalSnapshot(): ExpenseRefusalSnapshot? {
    val member = get(EXPENSE_PARTICIPATIONS.MEMBER) ?: return null
    return ExpenseRefusalSnapshot(
        member = MemberEmail.of(member),
        refusedAt = requireNotNull(get(EXPENSE_PARTICIPATIONS.DECIDED_AT)).toInstant(),
        reason = get(EXPENSE_PARTICIPATIONS.REFUSAL_REASON)?.let(RefusalReason::of),
    )
}
