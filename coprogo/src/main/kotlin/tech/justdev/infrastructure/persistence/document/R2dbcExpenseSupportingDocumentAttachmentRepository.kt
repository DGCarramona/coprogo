package tech.justdev.infrastructure.persistence.document

import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.Record4
import org.jooq.Record5
import org.jooq.Record6
import org.jooq.ResultQuery
import org.jooq.exception.IntegrityConstraintViolationException
import org.jooq.impl.DSL
import tech.justdev.domain.document.entity.ExpenseSupportingDocumentAttachment
import tech.justdev.domain.document.repository.ExpenseSupportingDocumentAttachmentRepository
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.document.valueobject.SupportingDocumentAttachmentDeletion
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.infrastructure.persistence.jooq.Tables.DOCUMENT_UPLOAD_INTENTS
import tech.justdev.infrastructure.persistence.jooq.Tables.EXPENSE_SUPPORTING_DOCUMENTS
import tech.justdev.infrastructure.persistence.jooq.Tables.SUPPORTING_DOCUMENT_ATTACHMENTS
import tech.justdev.infrastructure.persistence.jooq.dsl
import tech.justdev.infrastructure.persistence.jooq.enums.SupportingDocumentAttachmentType
import tech.justdev.infrastructure.persistence.jooq.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

private typealias AttachmentParentRecord = Record5<UUID, UUID, SupportingDocumentAttachmentType, String?, OffsetDateTime?>
private typealias ExpenseAttachmentRecord = Record4<UUID, UUID, UUID, UUID?>
private typealias ExpenseAttachmentWithDeletionRecord = Record6<UUID, UUID, UUID, UUID?, String?, OffsetDateTime?>

@Singleton
class R2dbcExpenseSupportingDocumentAttachmentRepository(
    @param:Named("default")
    private val connectionFactory: ConnectionFactory,
) : ExpenseSupportingDocumentAttachmentRepository {
    override suspend fun persist(attachment: ExpenseSupportingDocumentAttachment) = persistAll(listOf(attachment))

    override suspend fun persistAll(attachments: List<ExpenseSupportingDocumentAttachment>) {
        if (attachments.isEmpty()) return
        require(attachments.map(ExpenseSupportingDocumentAttachment::sourceUploadIntent).distinct().size == attachments.size) {
            "supporting document attachment upload intent identifiers must be unique"
        }

        connectionFactory.transaction {
            val dsl = connectionFactory.dsl()
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
                    attachments.map { attachment ->
                        DSL.row(
                            attachment.sourceUploadIntent.toPrimitive(),
                            attachment.group.toPrimitive(),
                            "CONSUMED",
                            SupportingDocumentAttachmentType.EXPENSE,
                            attachment.deletion?.deletedBy?.toPrimitive(),
                            attachment.deletion?.deletedAt?.atOffset(ZoneOffset.UTC),
                        )
                    },
                ).onConflict(SUPPORTING_DOCUMENT_ATTACHMENTS.SOURCE_UPLOAD_INTENT)
                .doUpdate()
                .set(SUPPORTING_DOCUMENT_ATTACHMENTS.DELETED_BY, DSL.excluded(SUPPORTING_DOCUMENT_ATTACHMENTS.DELETED_BY))
                .set(SUPPORTING_DOCUMENT_ATTACHMENTS.DELETED_AT, DSL.excluded(SUPPORTING_DOCUMENT_ATTACHMENTS.DELETED_AT))
                .where(SUPPORTING_DOCUMENT_ATTACHMENTS.DELETED_AT.isNull)
                .awaitFirstOrNull()

            val parents = findParents(dsl, attachments.map { it.sourceUploadIntent.toPrimitive() })
            check(parentAssociationsMatchExactly(parents, attachments)) {
                "supporting document attachment is already associated with another resource"
            }
            check(deletionAuditsMatchExactly(parents, attachments)) {
                "supporting document attachment deletion audit conflicts with persisted state"
            }

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
                        attachments.map { attachment ->
                            DSL.row(
                                attachment.sourceUploadIntent.toPrimitive(),
                                attachment.group.toPrimitive(),
                                SupportingDocumentAttachmentType.EXPENSE,
                                attachment.expense.toPrimitive(),
                                attachment.replacesSourceUploadIntent?.toPrimitive(),
                            )
                        },
                    ).onConflict(EXPENSE_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT)
                    .doNothing()
                    .awaitFirstOrNull()
            } catch (exception: IntegrityConstraintViolationException) {
                if (exception.sqlState() == "23505") {
                    throw IllegalStateException(
                        "expense supporting document attachment must replace a current document",
                        exception,
                    )
                }
                throw IllegalStateException(
                    "expense supporting document attachment must reference an expense in the same group",
                    exception,
                )
            }

            val persisted = findExpenseAttachmentsBySourceUploadIntents(dsl, attachments.map { it.sourceUploadIntent.toPrimitive() })
            check(expenseAttachmentsMatchExactly(persisted, attachments)) {
                "supporting document attachment is already associated with another expense"
            }
        }
    }

    override suspend fun findCurrentByExpenseAndGroup(
        expense: ExpenseId,
        group: GroupId,
    ): List<ExpenseSupportingDocumentAttachment> {
        val dsl = connectionFactory.dsl()
        val successor = EXPENSE_SUPPORTING_DOCUMENTS.`as`("successor")

        return dsl
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
            .and(SUPPORTING_DOCUMENT_ATTACHMENTS.GROUP.eq(EXPENSE_SUPPORTING_DOCUMENTS.GROUP))
            .join(DOCUMENT_UPLOAD_INTENTS)
            .on(DOCUMENT_UPLOAD_INTENTS.ID.eq(EXPENSE_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT))
            .and(DOCUMENT_UPLOAD_INTENTS.GROUP.eq(EXPENSE_SUPPORTING_DOCUMENTS.GROUP))
            .leftJoin(successor)
            .on(successor.REPLACES_SOURCE_UPLOAD_INTENT.eq(EXPENSE_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT))
            .where(EXPENSE_SUPPORTING_DOCUMENTS.EXPENSE.eq(expense.toPrimitive()))
            .and(EXPENSE_SUPPORTING_DOCUMENTS.GROUP.eq(group.toPrimitive()))
            .and(SUPPORTING_DOCUMENT_ATTACHMENTS.DELETED_AT.isNull)
            .and(successor.SOURCE_UPLOAD_INTENT.isNull)
            .orderBy(DOCUMENT_UPLOAD_INTENTS.CONSUMED_AT, EXPENSE_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT)
            .awaitList()
            .map { record -> record.toDomain() }
    }

    override suspend fun findCurrentBySourceUploadIntentAndExpenseAndGroup(
        sourceUploadIntent: DocumentUploadIntentId,
        expense: ExpenseId,
        group: GroupId,
    ): ExpenseSupportingDocumentAttachment? {
        val successor = EXPENSE_SUPPORTING_DOCUMENTS.`as`("successor")

        return connectionFactory
            .dsl()
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
            .and(SUPPORTING_DOCUMENT_ATTACHMENTS.GROUP.eq(EXPENSE_SUPPORTING_DOCUMENTS.GROUP))
            .leftJoin(successor)
            .on(successor.REPLACES_SOURCE_UPLOAD_INTENT.eq(EXPENSE_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT))
            .where(EXPENSE_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT.eq(sourceUploadIntent.toPrimitive()))
            .and(EXPENSE_SUPPORTING_DOCUMENTS.EXPENSE.eq(expense.toPrimitive()))
            .and(EXPENSE_SUPPORTING_DOCUMENTS.GROUP.eq(group.toPrimitive()))
            .and(SUPPORTING_DOCUMENT_ATTACHMENTS.DELETED_AT.isNull)
            .and(successor.SOURCE_UPLOAD_INTENT.isNull)
            .limit(1)
            .asFlow()
            .firstOrNull()
            ?.toDomain()
    }

    internal suspend fun findCurrentBySourceUploadIntentAndExpenseAndGroupForUpdate(
        sourceUploadIntent: DocumentUploadIntentId,
        expense: ExpenseId,
        group: GroupId,
    ): ExpenseSupportingDocumentAttachment? {
        val successor = EXPENSE_SUPPORTING_DOCUMENTS.`as`("successor")

        return connectionFactory
            .dsl()
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
            .and(SUPPORTING_DOCUMENT_ATTACHMENTS.GROUP.eq(EXPENSE_SUPPORTING_DOCUMENTS.GROUP))
            .where(EXPENSE_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT.eq(sourceUploadIntent.toPrimitive()))
            .and(EXPENSE_SUPPORTING_DOCUMENTS.EXPENSE.eq(expense.toPrimitive()))
            .and(EXPENSE_SUPPORTING_DOCUMENTS.GROUP.eq(group.toPrimitive()))
            .and(SUPPORTING_DOCUMENT_ATTACHMENTS.DELETED_AT.isNull)
            .and(
                DSL.notExists(
                    DSL
                        .selectOne()
                        .from(successor)
                        .where(successor.REPLACES_SOURCE_UPLOAD_INTENT.eq(EXPENSE_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT)),
                ),
            ).limit(1)
            .forUpdate()
            .asFlow()
            .firstOrNull()
            ?.toDomain()
    }

    override suspend fun findHistoryByExpenseAndGroup(
        expense: ExpenseId,
        group: GroupId,
    ): List<ExpenseSupportingDocumentAttachment> =
        connectionFactory
            .dsl()
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
            .and(SUPPORTING_DOCUMENT_ATTACHMENTS.GROUP.eq(EXPENSE_SUPPORTING_DOCUMENTS.GROUP))
            .join(DOCUMENT_UPLOAD_INTENTS)
            .on(DOCUMENT_UPLOAD_INTENTS.ID.eq(EXPENSE_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT))
            .and(DOCUMENT_UPLOAD_INTENTS.GROUP.eq(EXPENSE_SUPPORTING_DOCUMENTS.GROUP))
            .where(EXPENSE_SUPPORTING_DOCUMENTS.EXPENSE.eq(expense.toPrimitive()))
            .and(EXPENSE_SUPPORTING_DOCUMENTS.GROUP.eq(group.toPrimitive()))
            .orderBy(DOCUMENT_UPLOAD_INTENTS.CONSUMED_AT, EXPENSE_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT)
            .awaitList()
            .map { record -> record.toDomain() }
}

private suspend fun findParents(
    dsl: org.jooq.DSLContext,
    sourceUploadIntents: List<UUID>,
): List<AttachmentParentRecord> =
    dsl
        .select(
            SUPPORTING_DOCUMENT_ATTACHMENTS.SOURCE_UPLOAD_INTENT,
            SUPPORTING_DOCUMENT_ATTACHMENTS.GROUP,
            SUPPORTING_DOCUMENT_ATTACHMENTS.TYPE,
            SUPPORTING_DOCUMENT_ATTACHMENTS.DELETED_BY,
            SUPPORTING_DOCUMENT_ATTACHMENTS.DELETED_AT,
        ).from(SUPPORTING_DOCUMENT_ATTACHMENTS)
        .where(SUPPORTING_DOCUMENT_ATTACHMENTS.SOURCE_UPLOAD_INTENT.`in`(sourceUploadIntents))
        .awaitList()

private suspend fun findExpenseAttachmentsBySourceUploadIntents(
    dsl: org.jooq.DSLContext,
    sourceUploadIntents: List<UUID>,
): List<ExpenseAttachmentRecord> =
    dsl
        .select(
            EXPENSE_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT,
            EXPENSE_SUPPORTING_DOCUMENTS.GROUP,
            EXPENSE_SUPPORTING_DOCUMENTS.EXPENSE,
            EXPENSE_SUPPORTING_DOCUMENTS.REPLACES_SOURCE_UPLOAD_INTENT,
        ).from(EXPENSE_SUPPORTING_DOCUMENTS)
        .where(EXPENSE_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT.`in`(sourceUploadIntents))
        .awaitList()

private fun parentAssociationMatches(
    parent: AttachmentParentRecord,
    attachment: ExpenseSupportingDocumentAttachment,
): Boolean =
    parent.value1() == attachment.sourceUploadIntent.toPrimitive() &&
        parent.value2() == attachment.group.toPrimitive() &&
        parent.value3() == SupportingDocumentAttachmentType.EXPENSE

private fun deletionAuditMatches(
    parent: AttachmentParentRecord,
    attachment: ExpenseSupportingDocumentAttachment,
): Boolean =
    parent.value4() == attachment.deletion?.deletedBy?.toPrimitive() &&
        parent.value5()?.toInstant() == attachment.deletion?.deletedAt

private fun expenseAttachmentMatches(
    persisted: ExpenseAttachmentRecord,
    attachment: ExpenseSupportingDocumentAttachment,
): Boolean =
    persisted.value1() == attachment.sourceUploadIntent.toPrimitive() &&
        persisted.value2() == attachment.group.toPrimitive() &&
        persisted.value3() == attachment.expense.toPrimitive() &&
        persisted.value4() == attachment.replacesSourceUploadIntent?.toPrimitive()

private fun parentAssociationsMatchExactly(
    parents: List<AttachmentParentRecord>,
    attachments: List<ExpenseSupportingDocumentAttachment>,
): Boolean {
    val parentsByUploadIntent = parents.associateBy { it.value1() }
    return parents.size == attachments.size &&
        attachments.all { attachment ->
            parentsByUploadIntent[attachment.sourceUploadIntent.toPrimitive()]?.let { parentAssociationMatches(it, attachment) } == true
        }
}

private fun deletionAuditsMatchExactly(
    parents: List<AttachmentParentRecord>,
    attachments: List<ExpenseSupportingDocumentAttachment>,
): Boolean {
    val parentsByUploadIntent = parents.associateBy { it.value1() }
    return parents.size == attachments.size &&
        attachments.all { attachment ->
            parentsByUploadIntent[attachment.sourceUploadIntent.toPrimitive()]?.let { deletionAuditMatches(it, attachment) } == true
        }
}

private fun expenseAttachmentsMatchExactly(
    persisted: List<ExpenseAttachmentRecord>,
    attachments: List<ExpenseSupportingDocumentAttachment>,
): Boolean {
    val attachmentsByUploadIntent = persisted.associateBy { it.value1() }
    return persisted.size == attachments.size &&
        attachments.all { attachment ->
            attachmentsByUploadIntent[attachment.sourceUploadIntent.toPrimitive()]?.let { expenseAttachmentMatches(it, attachment) } == true
        }
}

private fun ExpenseAttachmentWithDeletionRecord.toDomain(): ExpenseSupportingDocumentAttachment =
    ExpenseSupportingDocumentAttachment.restore(
        sourceUploadIntent = DocumentUploadIntentId(value1()),
        group = GroupId(value2()),
        expense = ExpenseId(value3()),
        replacesSourceUploadIntent = value4()?.let(::DocumentUploadIntentId),
        deletion = deletionFromPersistence(value5(), value6()),
    )

private fun deletionFromPersistence(
    deletedBy: String?,
    deletedAt: OffsetDateTime?,
): SupportingDocumentAttachmentDeletion? {
    check((deletedBy == null) == (deletedAt == null)) {
        "supporting document attachment deletion audit must be complete"
    }

    return deletedBy?.let { SupportingDocumentAttachmentDeletion(MemberEmail.of(it), requireNotNull(deletedAt).toInstant()) }
}

private suspend fun <R : org.jooq.Record> ResultQuery<R>.awaitList(): List<R> = asFlow().toList()
