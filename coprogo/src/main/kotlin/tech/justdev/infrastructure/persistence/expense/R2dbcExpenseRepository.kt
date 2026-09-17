package tech.justdev.infrastructure.persistence.expense

import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.Record4
import org.jooq.ResultQuery
import tech.justdev.domain.document.entity.ExpenseSupportingDocumentAttachment
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.repository.ExpenseRepository
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.expense.valueobject.ExpenseParticipation
import tech.justdev.domain.expense.valueobject.ExpenseParticipationStatus
import tech.justdev.domain.expense.valueobject.ExpenseStatus
import tech.justdev.domain.expense.valueobject.RefusalReason
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.infrastructure.persistence.jooq.Tables.DOCUMENT_UPLOAD_INTENTS
import tech.justdev.infrastructure.persistence.jooq.Tables.EXPENSES
import tech.justdev.infrastructure.persistence.jooq.Tables.EXPENSE_PARTICIPATIONS
import tech.justdev.infrastructure.persistence.jooq.Tables.EXPENSE_SUPPORTING_DOCUMENTS
import tech.justdev.infrastructure.persistence.jooq.Tables.SUPPORTING_DOCUMENT_ATTACHMENTS
import tech.justdev.infrastructure.persistence.jooq.dsl
import tech.justdev.infrastructure.persistence.jooq.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import tech.justdev.infrastructure.persistence.jooq.enums.ExpenseParticipationStatus as JooqExpenseParticipationStatus
import tech.justdev.infrastructure.persistence.jooq.enums.ExpenseStatus as JooqExpenseStatus

private typealias CurrentExpenseSupportingDocumentRecord = Record4<UUID, UUID, UUID, UUID?>
private typealias JooqParticipationRecord = org.jooq.Record5<String, Long, JooqExpenseParticipationStatus, OffsetDateTime?, String?>

@Singleton
open class R2dbcExpenseRepository(
    @param:Named("default")
    private val connectionFactory: ConnectionFactory,
) : ExpenseRepository {
    override suspend fun findByIdAndGroup(
        id: ExpenseId,
        group: GroupId,
    ): Expense? {
        val dsl = connectionFactory.dsl()
        val expense =
            dsl
                .select(EXPENSES.ID, EXPENSES.GROUP, EXPENSES.TITLE, EXPENSES.CREATED_BY, EXPENSES.TOTAL_AMOUNT, EXPENSES.CREATED_AT)
                .from(EXPENSES)
                .where(EXPENSES.ID.eq(id.toPrimitive()))
                .and(EXPENSES.GROUP.eq(group.toPrimitive()))
                .awaitFirstOrNull()
                ?: return null

        val participations =
            dsl
                .select(
                    EXPENSE_PARTICIPATIONS.MEMBER,
                    EXPENSE_PARTICIPATIONS.AMOUNT,
                    EXPENSE_PARTICIPATIONS.STATUS,
                    EXPENSE_PARTICIPATIONS.DECIDED_AT,
                    EXPENSE_PARTICIPATIONS.REFUSAL_REASON,
                ).from(EXPENSE_PARTICIPATIONS)
                .where(EXPENSE_PARTICIPATIONS.EXPENSE.eq(id.toPrimitive()))
                .awaitList()
                .map(JooqParticipationRecord::toDomain)
                .toSet()

        return expense.toDomain(
            participations,
            dsl.findCurrentSupportingDocumentsByExpense(id, group),
        )
    }

    internal suspend fun findByIdAndGroupForUpdate(
        id: ExpenseId,
        group: GroupId,
    ): Expense? {
        val dsl = connectionFactory.dsl()
        val expense =
            dsl
                .select(EXPENSES.ID, EXPENSES.GROUP, EXPENSES.TITLE, EXPENSES.CREATED_BY, EXPENSES.TOTAL_AMOUNT, EXPENSES.CREATED_AT)
                .from(EXPENSES)
                .where(EXPENSES.ID.eq(id.toPrimitive()))
                .and(EXPENSES.GROUP.eq(group.toPrimitive()))
                .forUpdate()
                .awaitFirstOrNull()
                ?: return null

        val participations =
            dsl
                .select(
                    EXPENSE_PARTICIPATIONS.MEMBER,
                    EXPENSE_PARTICIPATIONS.AMOUNT,
                    EXPENSE_PARTICIPATIONS.STATUS,
                    EXPENSE_PARTICIPATIONS.DECIDED_AT,
                    EXPENSE_PARTICIPATIONS.REFUSAL_REASON,
                ).from(EXPENSE_PARTICIPATIONS)
                .where(EXPENSE_PARTICIPATIONS.EXPENSE.eq(id.toPrimitive()))
                .awaitList()
                .map(JooqParticipationRecord::toDomain)
                .toSet()

        return expense.toDomain(
            participations,
            dsl.findCurrentSupportingDocumentsByExpense(id, group, lock = true),
        )
    }

    override suspend fun findByGroup(group: GroupId): List<Expense> {
        val dsl = connectionFactory.dsl()
        val expenses =
            dsl
                .select(EXPENSES.ID, EXPENSES.GROUP, EXPENSES.TITLE, EXPENSES.CREATED_BY, EXPENSES.TOTAL_AMOUNT, EXPENSES.CREATED_AT)
                .from(EXPENSES)
                .where(EXPENSES.GROUP.eq(group.toPrimitive()))
                .orderBy(EXPENSES.CREATED_AT.desc(), EXPENSES.ID.desc())
                .awaitList()

        if (expenses.isEmpty()) return emptyList()

        val participationsByExpense =
            dsl
                .select(
                    EXPENSE_PARTICIPATIONS.EXPENSE,
                    EXPENSE_PARTICIPATIONS.MEMBER,
                    EXPENSE_PARTICIPATIONS.AMOUNT,
                    EXPENSE_PARTICIPATIONS.STATUS,
                    EXPENSE_PARTICIPATIONS.DECIDED_AT,
                    EXPENSE_PARTICIPATIONS.REFUSAL_REASON,
                ).from(EXPENSE_PARTICIPATIONS)
                .where(EXPENSE_PARTICIPATIONS.EXPENSE.`in`(expenses.map { it.value1() }))
                .awaitList()
                .groupBy(
                    keySelector = { it.value1() },
                    valueTransform = { it.toParticipationDomain() },
                )
        val supportingDocumentsByExpense =
            dsl
                .findCurrentSupportingDocumentsByExpenses(
                    expenses = expenses.map { record -> ExpenseId(record.value1()) },
                    group = group,
                ).groupBy(
                    keySelector = { (expense, _) -> expense },
                    valueTransform = { (_, supportingDocument) -> supportingDocument },
                )

        return expenses.map { expense ->
            expense.toDomain(
                participations = participationsByExpense[expense.value1()].orEmpty().toSet(),
                supportingDocuments = supportingDocumentsByExpense[ExpenseId(expense.value1())].orEmpty(),
            )
        }
    }

    override suspend fun findProposedByIdAndGroup(
        id: ExpenseId,
        group: GroupId,
    ): Expense? {
        val dsl = connectionFactory.dsl()
        val expense =
            dsl
                .select(EXPENSES.ID, EXPENSES.GROUP, EXPENSES.TITLE, EXPENSES.CREATED_BY, EXPENSES.TOTAL_AMOUNT, EXPENSES.CREATED_AT)
                .from(EXPENSES)
                .where(EXPENSES.ID.eq(id.toPrimitive()))
                .and(EXPENSES.GROUP.eq(group.toPrimitive()))
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
                    EXPENSE_PARTICIPATIONS.REFUSAL_REASON,
                ).from(EXPENSE_PARTICIPATIONS)
                .where(EXPENSE_PARTICIPATIONS.EXPENSE.eq(id.toPrimitive()))
                .awaitList()
                .map(JooqParticipationRecord::toDomain)
                .toSet()

        return expense.toDomain(
            participations,
            dsl.findCurrentSupportingDocumentsByExpense(id, group),
        )
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
                    EXPENSE_PARTICIPATIONS.REFUSAL_REASON,
                )
        expense.participations.forEach { participation ->
            insert.values(
                UUID.randomUUID(),
                expense.id.toPrimitive(),
                participation.member.toPrimitive(),
                participation.amount.inCents(),
                participation.status.toJooq(),
                participation.status.decidedAtOrNull()?.atOffset(ZoneOffset.UTC),
                participation.status.refusalReasonOrNull(),
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

private fun ExpenseParticipationStatus.refusalReasonOrNull(): String? =
    when (this) {
        ExpenseParticipationStatus.Pending -> null
        is ExpenseParticipationStatus.Approved -> null
        is ExpenseParticipationStatus.Refused -> reason?.toPrimitive()
    }

private fun org.jooq.Record6<UUID, UUID, String, String, Long, OffsetDateTime>.toDomain(
    participations: Set<ExpenseParticipation>,
    supportingDocuments: List<ExpenseSupportingDocumentAttachment>,
): Expense =
    Expense(
        id = ExpenseId(value1()),
        group = GroupId(value2()),
        title = value3(),
        createdBy = MemberEmail.of(value4()),
        totalAmount = MoneyAmount.ofCents(value5()),
        createdAt = value6().toInstant(),
        participations = participations,
        supportingDocuments = supportingDocuments,
    )

private suspend fun DSLContext.findCurrentSupportingDocumentsByExpense(
    expense: ExpenseId,
    group: GroupId,
    lock: Boolean = false,
): List<ExpenseSupportingDocumentAttachment> =
    findCurrentSupportingDocumentsByExpenses(
        expenses = listOf(expense),
        group = group,
        lock = lock,
    ).map { (_, supportingDocument) -> supportingDocument }

private suspend fun DSLContext.findCurrentSupportingDocumentsByExpenses(
    expenses: List<ExpenseId>,
    group: GroupId,
    lock: Boolean = false,
): List<Pair<ExpenseId, ExpenseSupportingDocumentAttachment>> {
    if (expenses.isEmpty()) return emptyList()

    val successor = EXPENSE_SUPPORTING_DOCUMENTS.`as`("successor")
    return this
        .select(
            EXPENSE_SUPPORTING_DOCUMENTS.EXPENSE,
            EXPENSE_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT,
            EXPENSE_SUPPORTING_DOCUMENTS.GROUP,
            EXPENSE_SUPPORTING_DOCUMENTS.REPLACES_SOURCE_UPLOAD_INTENT,
        ).from(EXPENSE_SUPPORTING_DOCUMENTS)
        .join(SUPPORTING_DOCUMENT_ATTACHMENTS)
        .on(SUPPORTING_DOCUMENT_ATTACHMENTS.SOURCE_UPLOAD_INTENT.eq(EXPENSE_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT))
        .and(SUPPORTING_DOCUMENT_ATTACHMENTS.GROUP.eq(EXPENSE_SUPPORTING_DOCUMENTS.GROUP))
        .join(DOCUMENT_UPLOAD_INTENTS)
        .on(DOCUMENT_UPLOAD_INTENTS.ID.eq(EXPENSE_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT))
        .and(DOCUMENT_UPLOAD_INTENTS.GROUP.eq(EXPENSE_SUPPORTING_DOCUMENTS.GROUP))
        .where(EXPENSE_SUPPORTING_DOCUMENTS.EXPENSE.`in`(expenses.map(ExpenseId::toPrimitive)))
        .and(EXPENSE_SUPPORTING_DOCUMENTS.GROUP.eq(group.toPrimitive()))
        .and(SUPPORTING_DOCUMENT_ATTACHMENTS.DELETED_AT.isNull)
        .and(
            org.jooq.impl.DSL.notExists(
                org.jooq.impl.DSL
                    .selectOne()
                    .from(successor)
                    .where(successor.REPLACES_SOURCE_UPLOAD_INTENT.eq(EXPENSE_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT)),
            ),
        ).orderBy(
            EXPENSE_SUPPORTING_DOCUMENTS.EXPENSE,
            DOCUMENT_UPLOAD_INTENTS.CONSUMED_AT,
            EXPENSE_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT,
        ).run { if (lock) forUpdate().awaitList() else awaitList() }
        .map(CurrentExpenseSupportingDocumentRecord::toCurrentSupportingDocument)
}

private fun CurrentExpenseSupportingDocumentRecord.toCurrentSupportingDocument(): Pair<ExpenseId, ExpenseSupportingDocumentAttachment> =
    ExpenseId(value1()) to
        ExpenseSupportingDocumentAttachment.restore(
            sourceUploadIntent = DocumentUploadIntentId(value2()),
            group = GroupId(value3()),
            expense = ExpenseId(value1()),
            replacesSourceUploadIntent = value4()?.let(::DocumentUploadIntentId),
        )

@Suppress("ktlint:standard:max-line-length")
private fun org.jooq.Record6<UUID, String, Long, JooqExpenseParticipationStatus, OffsetDateTime?, String?>.toParticipationDomain(): ExpenseParticipation =
    ExpenseParticipation(
        member = MemberEmail.of(value2()),
        amount = MoneyAmount.ofCents(value3()),
        status = value4().toDomain(value5()?.toInstant(), value6()),
    )

@Suppress("ktlint:standard:max-line-length")
private fun JooqParticipationRecord.toDomain(): ExpenseParticipation =
    ExpenseParticipation(
        member = MemberEmail.of(value1()),
        amount = MoneyAmount.ofCents(value2()),
        status = value3().toDomain(value4()?.toInstant(), value5()),
    )

private fun JooqExpenseParticipationStatus.toDomain(
    decidedAt: java.time.Instant?,
    refusalReason: String?,
): ExpenseParticipationStatus =
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
                refusalReason?.let(RefusalReason::of),
            )
        }
    }

private suspend fun <R : Record> ResultQuery<R>.awaitList(): List<R> = asFlow().toList()
