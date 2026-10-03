package tech.justdev.infrastructure.persistence.expense

import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.Record6
import org.jooq.exception.IntegrityConstraintViolationException
import org.jooq.impl.DSL
import tech.justdev.domain.document.entity.DocumentMetadata
import tech.justdev.domain.document.entity.ExpenseSupportingDocument
import tech.justdev.domain.document.entity.ExpenseSupportingDocuments
import tech.justdev.domain.document.valueobject.DocumentFileName
import tech.justdev.domain.document.valueobject.DocumentMediaType
import tech.justdev.domain.document.valueobject.DocumentSha256
import tech.justdev.domain.document.valueobject.DocumentSize
import tech.justdev.domain.document.valueobject.DocumentStorageKey
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.document.valueobject.SupportingDocumentAttachmentDeletion
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
import tech.justdev.infrastructure.persistence.jooq.awaitList
import tech.justdev.infrastructure.persistence.jooq.dsl
import tech.justdev.infrastructure.persistence.jooq.enums.SupportingDocumentAttachmentType
import tech.justdev.infrastructure.persistence.jooq.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import tech.justdev.infrastructure.persistence.jooq.enums.ExpenseParticipationStatus as JooqExpenseParticipationStatus
import tech.justdev.infrastructure.persistence.jooq.enums.ExpenseStatus as JooqExpenseStatus

private typealias JooqParticipationRecord = org.jooq.Record5<String, Long, JooqExpenseParticipationStatus, OffsetDateTime?, String?>
private typealias PersistedSupportingDocumentRecord = Record6<UUID, UUID, UUID, UUID?, String?, OffsetDateTime?>

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
            dsl.findSupportingDocumentHistoryByExpense(id, group),
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
            dsl.findSupportingDocumentHistoryByExpense(id, group, lock = true),
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
                .findSupportingDocumentHistoryByExpenses(
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
            dsl.findSupportingDocumentHistoryByExpense(id, group),
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

        persistSupportingDocuments(dsl, expense)
    }
}

private suspend fun persistSupportingDocuments(
    dsl: DSLContext,
    expense: Expense,
) {
    val supportingDocuments = expense.supportingDocuments.all
    if (supportingDocuments.isEmpty()) return

    dsl
        .insertInto(SUPPORTING_DOCUMENT_ATTACHMENTS)
        .columns(
            SUPPORTING_DOCUMENT_ATTACHMENTS.SOURCE_UPLOAD_INTENT,
            SUPPORTING_DOCUMENT_ATTACHMENTS.GROUP,
            SUPPORTING_DOCUMENT_ATTACHMENTS.SOURCE_STATUS,
            SUPPORTING_DOCUMENT_ATTACHMENTS.TYPE,
            SUPPORTING_DOCUMENT_ATTACHMENTS.DELETED_BY,
            SUPPORTING_DOCUMENT_ATTACHMENTS.DELETED_AT,
        ).valuesOfRows(
            supportingDocuments.map { document ->
                DSL.row(
                    document.sourceUploadIntent.toPrimitive(),
                    expense.group.toPrimitive(),
                    "CONSUMED",
                    SupportingDocumentAttachmentType.EXPENSE,
                    document.deletion?.deletedBy?.toPrimitive(),
                    document.deletion?.deletedAt?.atOffset(ZoneOffset.UTC),
                )
            },
        ).onConflict(SUPPORTING_DOCUMENT_ATTACHMENTS.SOURCE_UPLOAD_INTENT)
        .doUpdate()
        .set(SUPPORTING_DOCUMENT_ATTACHMENTS.DELETED_BY, DSL.excluded(SUPPORTING_DOCUMENT_ATTACHMENTS.DELETED_BY))
        .set(SUPPORTING_DOCUMENT_ATTACHMENTS.DELETED_AT, DSL.excluded(SUPPORTING_DOCUMENT_ATTACHMENTS.DELETED_AT))
        .where(SUPPORTING_DOCUMENT_ATTACHMENTS.DELETED_AT.isNull)
        .awaitFirstOrNull()

    try {
        dsl
            .insertInto(EXPENSE_SUPPORTING_DOCUMENTS)
            .columns(
                EXPENSE_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT,
                EXPENSE_SUPPORTING_DOCUMENTS.GROUP,
                EXPENSE_SUPPORTING_DOCUMENTS.TYPE,
                EXPENSE_SUPPORTING_DOCUMENTS.EXPENSE,
                EXPENSE_SUPPORTING_DOCUMENTS.REPLACES_SOURCE_UPLOAD_INTENT,
            ).valuesOfRows(
                supportingDocuments.map { document ->
                    DSL.row(
                        document.sourceUploadIntent.toPrimitive(),
                        expense.group.toPrimitive(),
                        SupportingDocumentAttachmentType.EXPENSE,
                        expense.id.toPrimitive(),
                        document.replacesSourceUploadIntent?.toPrimitive(),
                    )
                },
            ).onConflict(EXPENSE_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT)
            .doNothing()
            .awaitFirstOrNull()
    } catch (exception: IntegrityConstraintViolationException) {
        if (exception.sqlState() == "23505") {
            throw IllegalStateException(
                "expense supporting document must replace a current document",
                exception,
            )
        }
        throw IllegalStateException(
            "expense supporting document must reference an expense in the same group",
            exception,
        )
    }

    val persisted =
        dsl
            .select(
                EXPENSE_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT,
                EXPENSE_SUPPORTING_DOCUMENTS.GROUP,
                EXPENSE_SUPPORTING_DOCUMENTS.EXPENSE,
                EXPENSE_SUPPORTING_DOCUMENTS.REPLACES_SOURCE_UPLOAD_INTENT,
                SUPPORTING_DOCUMENT_ATTACHMENTS.DELETED_BY,
                SUPPORTING_DOCUMENT_ATTACHMENTS.DELETED_AT,
            ).from(EXPENSE_SUPPORTING_DOCUMENTS)
            .join(SUPPORTING_DOCUMENT_ATTACHMENTS)
            .on(SUPPORTING_DOCUMENT_ATTACHMENTS.SOURCE_UPLOAD_INTENT.eq(EXPENSE_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT))
            .where(EXPENSE_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT.`in`(supportingDocuments.map { it.sourceUploadIntent.toPrimitive() }))
            .awaitList()

    check(persistedSupportingDocumentsMatch(expense, supportingDocuments, persisted)) {
        "supporting document is already associated with another expense or has conflicting deletion audit data"
    }
}

private fun persistedSupportingDocumentsMatch(
    expense: Expense,
    supportingDocuments: List<ExpenseSupportingDocument>,
    persisted: List<PersistedSupportingDocumentRecord>,
): Boolean {
    val documentsBySourceUploadIntent = supportingDocuments.associateBy { document -> document.sourceUploadIntent.toPrimitive() }

    return persisted.size == supportingDocuments.size &&
        persisted.all { record ->
            documentsBySourceUploadIntent[record.value1()]
                ?.let { document ->
                    record.value2() == expense.group.toPrimitive() &&
                        record.value3() == expense.id.toPrimitive() &&
                        record.value4() == document.replacesSourceUploadIntent?.toPrimitive() &&
                        record.value5() == document.deletion?.deletedBy?.toPrimitive() &&
                        record.value6()?.toInstant() == document.deletion?.deletedAt
                } == true
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
    supportingDocuments: List<ExpenseSupportingDocument>,
): Expense =
    Expense(
        id = ExpenseId(value1()),
        group = GroupId(value2()),
        title = value3(),
        createdBy = MemberEmail.of(value4()),
        totalAmount = MoneyAmount.ofCents(value5()),
        createdAt = value6().toInstant(),
        participations = participations,
        supportingDocuments = ExpenseSupportingDocuments.restore(supportingDocuments),
    )

private suspend fun DSLContext.findSupportingDocumentHistoryByExpense(
    expense: ExpenseId,
    group: GroupId,
    lock: Boolean = false,
): List<ExpenseSupportingDocument> =
    findSupportingDocumentHistoryByExpenses(
        expenses = listOf(expense),
        group = group,
        lock = lock,
    ).map { (_, supportingDocument) -> supportingDocument }

private suspend fun DSLContext.findSupportingDocumentHistoryByExpenses(
    expenses: List<ExpenseId>,
    group: GroupId,
    lock: Boolean = false,
): List<Pair<ExpenseId, ExpenseSupportingDocument>> {
    if (expenses.isEmpty()) return emptyList()

    return this
        .select(
            EXPENSE_SUPPORTING_DOCUMENTS.EXPENSE,
            EXPENSE_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT,
            EXPENSE_SUPPORTING_DOCUMENTS.GROUP,
            EXPENSE_SUPPORTING_DOCUMENTS.REPLACES_SOURCE_UPLOAD_INTENT,
            SUPPORTING_DOCUMENT_ATTACHMENTS.DELETED_BY,
            SUPPORTING_DOCUMENT_ATTACHMENTS.DELETED_AT,
            DOCUMENT_UPLOAD_INTENTS.ID,
            DOCUMENT_UPLOAD_INTENTS.GROUP,
            DOCUMENT_UPLOAD_INTENTS.UPLOADER,
            DOCUMENT_UPLOAD_INTENTS.STORAGE_KEY,
            DOCUMENT_UPLOAD_INTENTS.FILE_NAME,
            DOCUMENT_UPLOAD_INTENTS.MEDIA_TYPE,
            DOCUMENT_UPLOAD_INTENTS.EXPECTED_SIZE,
            DOCUMENT_UPLOAD_INTENTS.EXPECTED_SHA256,
            DOCUMENT_UPLOAD_INTENTS.CREATED_AT,
            DOCUMENT_UPLOAD_INTENTS.EXPIRES_AT,
            DOCUMENT_UPLOAD_INTENTS.STATUS,
            DOCUMENT_UPLOAD_INTENTS.READY_AT,
            DOCUMENT_UPLOAD_INTENTS.CONSUMED_AT,
        ).from(EXPENSE_SUPPORTING_DOCUMENTS)
        .join(SUPPORTING_DOCUMENT_ATTACHMENTS)
        .on(SUPPORTING_DOCUMENT_ATTACHMENTS.SOURCE_UPLOAD_INTENT.eq(EXPENSE_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT))
        .and(SUPPORTING_DOCUMENT_ATTACHMENTS.GROUP.eq(EXPENSE_SUPPORTING_DOCUMENTS.GROUP))
        .join(DOCUMENT_UPLOAD_INTENTS)
        .on(DOCUMENT_UPLOAD_INTENTS.ID.eq(EXPENSE_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT))
        .and(DOCUMENT_UPLOAD_INTENTS.GROUP.eq(EXPENSE_SUPPORTING_DOCUMENTS.GROUP))
        .where(EXPENSE_SUPPORTING_DOCUMENTS.EXPENSE.`in`(expenses.map(ExpenseId::toPrimitive)))
        .and(EXPENSE_SUPPORTING_DOCUMENTS.GROUP.eq(group.toPrimitive()))
        .orderBy(
            EXPENSE_SUPPORTING_DOCUMENTS.EXPENSE,
            DOCUMENT_UPLOAD_INTENTS.CONSUMED_AT,
            EXPENSE_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT,
        ).run { if (lock) forUpdate().awaitList() else awaitList() }
        .map(::toSupportingDocumentHistory)
}

private fun toSupportingDocumentHistory(record: Record): Pair<ExpenseId, ExpenseSupportingDocument> {
    require(record.get(DOCUMENT_UPLOAD_INTENTS.STATUS) == "CONSUMED") {
        "expense supporting document upload intent must be consumed"
    }

    return ExpenseId(record.get(EXPENSE_SUPPORTING_DOCUMENTS.EXPENSE)) to
        ExpenseSupportingDocument.restore(
            sourceUploadIntent = DocumentUploadIntentId(record.get(DOCUMENT_UPLOAD_INTENTS.ID)),
            uploader = MemberEmail.of(record.get(DOCUMENT_UPLOAD_INTENTS.UPLOADER)),
            storageKey = DocumentStorageKey.of(record.get(DOCUMENT_UPLOAD_INTENTS.STORAGE_KEY)),
            fileName = DocumentFileName.of(record.get(DOCUMENT_UPLOAD_INTENTS.FILE_NAME)),
            metadata =
                DocumentMetadata(
                    mediaType = DocumentMediaType.of(record.get(DOCUMENT_UPLOAD_INTENTS.MEDIA_TYPE)),
                    size = DocumentSize.ofBytes(record.get(DOCUMENT_UPLOAD_INTENTS.EXPECTED_SIZE)),
                    checksum = DocumentSha256.fromBase64(record.get(DOCUMENT_UPLOAD_INTENTS.EXPECTED_SHA256)),
                ),
            attachedAt = requireNotNull(record.get(DOCUMENT_UPLOAD_INTENTS.CONSUMED_AT)).toInstant(),
            replacesSourceUploadIntent =
                record
                    .get(EXPENSE_SUPPORTING_DOCUMENTS.REPLACES_SOURCE_UPLOAD_INTENT)
                    ?.let(::DocumentUploadIntentId),
            deletion =
                record.get(SUPPORTING_DOCUMENT_ATTACHMENTS.DELETED_BY)?.let { deletedBy ->
                    SupportingDocumentAttachmentDeletion(
                        deletedBy = MemberEmail.of(deletedBy),
                        deletedAt = requireNotNull(record.get(SUPPORTING_DOCUMENT_ATTACHMENTS.DELETED_AT)).toInstant(),
                    )
                },
        )
}

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
