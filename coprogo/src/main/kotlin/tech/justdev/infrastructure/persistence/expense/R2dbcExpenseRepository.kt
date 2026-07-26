package tech.justdev.infrastructure.persistence.expense

import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.Record
import org.jooq.ResultQuery
import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.repository.ExpenseRepository
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.expense.valueobject.ExpenseParticipation
import tech.justdev.domain.expense.valueobject.ExpenseParticipationStatus
import tech.justdev.domain.expense.valueobject.ExpenseStatus
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.infrastructure.persistence.jooq.Tables.EXPENSES
import tech.justdev.infrastructure.persistence.jooq.Tables.EXPENSE_PARTICIPATIONS
import tech.justdev.infrastructure.persistence.jooq.dsl
import tech.justdev.infrastructure.persistence.jooq.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import tech.justdev.infrastructure.persistence.jooq.enums.ExpenseParticipationStatus as JooqExpenseParticipationStatus
import tech.justdev.infrastructure.persistence.jooq.enums.ExpenseStatus as JooqExpenseStatus

@Singleton
open class R2dbcExpenseRepository(
    @param:Named("default")
    private val connectionFactory: ConnectionFactory,
) : ExpenseRepository {
    override suspend fun findById(id: ExpenseId): Expense? {
        val dsl = connectionFactory.dsl()
        val expense =
            dsl
                .select(EXPENSES.ID, EXPENSES.GROUP, EXPENSES.TITLE, EXPENSES.CREATED_BY, EXPENSES.TOTAL_AMOUNT, EXPENSES.CREATED_AT)
                .from(EXPENSES)
                .where(EXPENSES.ID.eq(id.toPrimitive()))
                .awaitFirstOrNull()
                ?: return null

        val participations =
            dsl
                .select(
                    EXPENSE_PARTICIPATIONS.MEMBER,
                    EXPENSE_PARTICIPATIONS.AMOUNT,
                    EXPENSE_PARTICIPATIONS.STATUS,
                    EXPENSE_PARTICIPATIONS.DECIDED_AT,
                ).from(EXPENSE_PARTICIPATIONS)
                .where(EXPENSE_PARTICIPATIONS.EXPENSE.eq(id.toPrimitive()))
                .awaitList()
                .map { it.toDomain() }
                .toSet()

        return expense.toDomain(participations)
    }

    override suspend fun findProposedById(id: ExpenseId): Expense? {
        val dsl = connectionFactory.dsl()
        val expense =
            dsl
                .select(EXPENSES.ID, EXPENSES.GROUP, EXPENSES.TITLE, EXPENSES.CREATED_BY, EXPENSES.TOTAL_AMOUNT, EXPENSES.CREATED_AT)
                .from(EXPENSES)
                .where(EXPENSES.ID.eq(id.toPrimitive()))
                .and(EXPENSES.STATUS.eq(tech.justdev.infrastructure.persistence.jooq.enums.ExpenseStatus.PROPOSED))
                .awaitFirstOrNull()
                ?: return null

        val participations =
            dsl
                .select(
                    EXPENSE_PARTICIPATIONS.MEMBER,
                    EXPENSE_PARTICIPATIONS.AMOUNT,
                    EXPENSE_PARTICIPATIONS.STATUS,
                    EXPENSE_PARTICIPATIONS.DECIDED_AT,
                ).from(EXPENSE_PARTICIPATIONS)
                .where(EXPENSE_PARTICIPATIONS.EXPENSE.eq(id.toPrimitive()))
                .awaitList()
                .map { it.toDomain() }
                .toSet()

        return expense.toDomain(participations)
    }

    override suspend fun persist(expense: Expense) {
        connectionFactory.transaction {
            persistInTransaction(expense)
        }
    }

    private suspend fun persistInTransaction(expense: Expense) {
        val dsl = connectionFactory.dsl()

        dsl
            .insertInto(EXPENSES)
            .columns(
                EXPENSES.ID,
                EXPENSES.GROUP,
                EXPENSES.TITLE,
                EXPENSES.CREATED_BY,
                EXPENSES.TOTAL_AMOUNT,
                EXPENSES.STATUS,
                EXPENSES.CREATED_AT,
            ).values(
                expense.id.toPrimitive(),
                expense.group.toPrimitive(),
                expense.title,
                expense.createdBy.toPrimitive(),
                expense.totalAmount.inCents(),
                expense.status.toJooq(),
                expense.createdAt.atOffset(ZoneOffset.UTC),
            ).onConflict(EXPENSES.ID)
            .doUpdate()
            .set(EXPENSES.GROUP, expense.group.toPrimitive())
            .set(EXPENSES.TITLE, expense.title)
            .set(EXPENSES.CREATED_BY, expense.createdBy.toPrimitive())
            .set(EXPENSES.TOTAL_AMOUNT, expense.totalAmount.inCents())
            .set(EXPENSES.STATUS, expense.status.toJooq())
            .set(EXPENSES.CREATED_AT, expense.createdAt.atOffset(ZoneOffset.UTC))
            .awaitFirstOrNull()

        dsl
            .deleteFrom(EXPENSE_PARTICIPATIONS)
            .where(EXPENSE_PARTICIPATIONS.EXPENSE.eq(expense.id.toPrimitive()))
            .awaitFirstOrNull()

        if (expense.participations.isEmpty()) return
        val insert =
            dsl
                .insertInto(EXPENSE_PARTICIPATIONS)
                .columns(
                    EXPENSE_PARTICIPATIONS.ID,
                    EXPENSE_PARTICIPATIONS.EXPENSE,
                    EXPENSE_PARTICIPATIONS.MEMBER,
                    EXPENSE_PARTICIPATIONS.AMOUNT,
                    EXPENSE_PARTICIPATIONS.STATUS,
                    EXPENSE_PARTICIPATIONS.DECIDED_AT,
                )
        expense.participations.forEach { participation ->
            insert.values(
                UUID.randomUUID(),
                expense.id.toPrimitive(),
                participation.member.toPrimitive(),
                participation.amount.inCents(),
                participation.status.toJooq(),
                participation.status.decidedAtOrNull()?.atOffset(ZoneOffset.UTC),
            )
        }
        insert.awaitFirstOrNull()
    }
}

private fun ExpenseStatus.toJooq(): JooqExpenseStatus =
    when (this) {
        ExpenseStatus.PROPOSED -> JooqExpenseStatus.PROPOSED
        ExpenseStatus.ACCEPTED -> JooqExpenseStatus.ACCEPTED
        ExpenseStatus.INVALIDATED -> JooqExpenseStatus.INVALIDATED
    }

private fun ExpenseParticipationStatus.toJooq(): JooqExpenseParticipationStatus =
    when (this) {
        is ExpenseParticipationStatus.Pending -> JooqExpenseParticipationStatus.PENDING
        is ExpenseParticipationStatus.Approved -> JooqExpenseParticipationStatus.APPROVED
        is ExpenseParticipationStatus.Refused -> JooqExpenseParticipationStatus.REFUSED
    }

private fun ExpenseParticipationStatus.decidedAtOrNull(): java.time.Instant? =
    when (this) {
        is ExpenseParticipationStatus.Pending -> null
        is ExpenseParticipationStatus.Approved -> decidedAt
        is ExpenseParticipationStatus.Refused -> decidedAt
    }

private fun org.jooq.Record6<UUID, UUID, String, String, Long, OffsetDateTime>.toDomain(
    participations: Set<ExpenseParticipation>,
): Expense =
    Expense(
        id = ExpenseId(value1()),
        group = GroupId(value2()),
        title = value3(),
        createdBy = MemberEmail.of(value4()),
        totalAmount = MoneyAmount.ofCents(value5()),
        createdAt = value6().toInstant(),
        participations = participations,
    )

private fun org.jooq.Record4<String, Long, JooqExpenseParticipationStatus, OffsetDateTime?>.toDomain(): ExpenseParticipation =
    ExpenseParticipation(
        member = MemberEmail.of(value1()),
        amount = MoneyAmount.ofCents(value2()),
        status = value3().toDomain(value4()?.toInstant()),
    )

private fun JooqExpenseParticipationStatus.toDomain(decidedAt: java.time.Instant?): ExpenseParticipationStatus =
    when (this) {
        JooqExpenseParticipationStatus.PENDING -> {
            ExpenseParticipationStatus.Pending
        }

        JooqExpenseParticipationStatus.APPROVED -> {
            ExpenseParticipationStatus.Approved(
                decidedAt!!,
            )
        }

        JooqExpenseParticipationStatus.REFUSED -> {
            ExpenseParticipationStatus.Refused(
                decidedAt!!,
            )
        }
    }

private suspend fun <R : Record> ResultQuery<R>.awaitList(): List<R> = asFlow().toList()
