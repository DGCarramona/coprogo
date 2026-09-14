package tech.justdev.infrastructure.persistence.document

import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.Record3
import org.jooq.ResultQuery
import org.jooq.exception.IntegrityConstraintViolationException
import org.jooq.impl.DSL
import tech.justdev.domain.document.entity.ExpenseSupportingDocumentAttachment
import tech.justdev.domain.document.repository.ExpenseSupportingDocumentAttachmentRepository
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.infrastructure.persistence.jooq.Tables.EXPENSE_SUPPORTING_DOCUMENTS
import tech.justdev.infrastructure.persistence.jooq.Tables.SUPPORTING_DOCUMENT_ATTACHMENTS
import tech.justdev.infrastructure.persistence.jooq.dsl
import tech.justdev.infrastructure.persistence.jooq.enums.SupportingDocumentAttachmentType
import tech.justdev.infrastructure.persistence.jooq.transaction
import java.util.UUID

private typealias AttachmentParentRecord = Record3<UUID, UUID, SupportingDocumentAttachmentType>
private typealias ExpenseAttachmentRecord = Record3<UUID, UUID, UUID>

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
                ).valuesOfRows(
                    attachments.map { attachment ->
                        DSL.row(
                            attachment.sourceUploadIntent.toPrimitive(),
                            attachment.group.toPrimitive(),
                            "CONSUMED",
                            SupportingDocumentAttachmentType.EXPENSE,
                        )
                    },
                ).onConflict(SUPPORTING_DOCUMENT_ATTACHMENTS.SOURCE_UPLOAD_INTENT)
                .doNothing()
                .awaitFirstOrNull()

            val parents = dsl.findParents(attachments.map { it.sourceUploadIntent.toPrimitive() })
            check(parents.matchParentsExactly(attachments)) {
                "supporting document attachment is already associated with another resource"
            }

            try {
                dsl
                    .insertInto(EXPENSE_SUPPORTING_DOCUMENTS)
                    .columns(
                        EXPENSE_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT,
                        EXPENSE_SUPPORTING_DOCUMENTS.GROUP,
                        EXPENSE_SUPPORTING_DOCUMENTS.TYPE,
                        EXPENSE_SUPPORTING_DOCUMENTS.EXPENSE,
                    ).valuesOfRows(
                        attachments.map { attachment ->
                            DSL.row(
                                attachment.sourceUploadIntent.toPrimitive(),
                                attachment.group.toPrimitive(),
                                SupportingDocumentAttachmentType.EXPENSE,
                                attachment.expense.toPrimitive(),
                            )
                        },
                    ).onConflict(EXPENSE_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT)
                    .doNothing()
                    .awaitFirstOrNull()
            } catch (exception: IntegrityConstraintViolationException) {
                throw IllegalStateException(
                    "expense supporting document attachment must reference an expense in the same group",
                    exception,
                )
            }

            val persisted = dsl.findExpenseAttachmentsBySourceUploadIntents(attachments.map { it.sourceUploadIntent.toPrimitive() })
            check(persisted.matchExpenseAttachmentsExactly(attachments)) {
                "supporting document attachment is already associated with another expense"
            }
        }
    }

    override suspend fun findByExpenseAndGroup(
        expense: ExpenseId,
        group: GroupId,
    ): List<ExpenseSupportingDocumentAttachment> =
        connectionFactory
            .dsl()
            .findExpenseAttachments(expense.toPrimitive(), group.toPrimitive())
            .map { record -> record.toDomain() }
}

private suspend fun org.jooq.DSLContext.findParents(sourceUploadIntents: List<UUID>): List<AttachmentParentRecord> =
    select(
        SUPPORTING_DOCUMENT_ATTACHMENTS.SOURCE_UPLOAD_INTENT,
        SUPPORTING_DOCUMENT_ATTACHMENTS.GROUP,
        SUPPORTING_DOCUMENT_ATTACHMENTS.TYPE,
    ).from(SUPPORTING_DOCUMENT_ATTACHMENTS)
        .where(SUPPORTING_DOCUMENT_ATTACHMENTS.SOURCE_UPLOAD_INTENT.`in`(sourceUploadIntents))
        .awaitList()

private suspend fun org.jooq.DSLContext.findExpenseAttachmentsBySourceUploadIntents(
    sourceUploadIntents: List<UUID>,
): List<ExpenseAttachmentRecord> =
    select(
        EXPENSE_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT,
        EXPENSE_SUPPORTING_DOCUMENTS.GROUP,
        EXPENSE_SUPPORTING_DOCUMENTS.EXPENSE,
    ).from(EXPENSE_SUPPORTING_DOCUMENTS)
        .where(EXPENSE_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT.`in`(sourceUploadIntents))
        .awaitList()

private suspend fun org.jooq.DSLContext.findExpenseAttachments(
    expense: UUID,
    group: UUID,
): List<ExpenseAttachmentRecord> =
    select(
        EXPENSE_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT,
        EXPENSE_SUPPORTING_DOCUMENTS.GROUP,
        EXPENSE_SUPPORTING_DOCUMENTS.EXPENSE,
    ).from(EXPENSE_SUPPORTING_DOCUMENTS)
        .where(EXPENSE_SUPPORTING_DOCUMENTS.EXPENSE.eq(expense))
        .and(EXPENSE_SUPPORTING_DOCUMENTS.GROUP.eq(group))
        .orderBy(EXPENSE_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT)
        .awaitList()

private fun AttachmentParentRecord.matchesParent(attachment: ExpenseSupportingDocumentAttachment): Boolean =
    value1() == attachment.sourceUploadIntent.toPrimitive() &&
        value2() == attachment.group.toPrimitive() &&
        value3() == SupportingDocumentAttachmentType.EXPENSE

private fun ExpenseAttachmentRecord.matchesExpenseAttachment(attachment: ExpenseSupportingDocumentAttachment): Boolean =
    value1() == attachment.sourceUploadIntent.toPrimitive() &&
        value2() == attachment.group.toPrimitive() &&
        value3() == attachment.expense.toPrimitive()

private fun List<AttachmentParentRecord>.matchParentsExactly(attachments: List<ExpenseSupportingDocumentAttachment>): Boolean {
    val parentsByUploadIntent = associateBy { it.value1() }
    return size == attachments.size &&
        attachments.all { attachment ->
            parentsByUploadIntent[attachment.sourceUploadIntent.toPrimitive()]?.matchesParent(attachment) == true
        }
}

private fun List<ExpenseAttachmentRecord>.matchExpenseAttachmentsExactly(attachments: List<ExpenseSupportingDocumentAttachment>): Boolean {
    val attachmentsByUploadIntent = associateBy { it.value1() }
    return size == attachments.size &&
        attachments.all { attachment ->
            attachmentsByUploadIntent[attachment.sourceUploadIntent.toPrimitive()]?.matchesExpenseAttachment(attachment) == true
        }
}

private fun ExpenseAttachmentRecord.toDomain(): ExpenseSupportingDocumentAttachment =
    ExpenseSupportingDocumentAttachment.restore(
        sourceUploadIntent = DocumentUploadIntentId(value1()),
        group = GroupId(value2()),
        expense = ExpenseId(value3()),
    )

private suspend fun <R : org.jooq.Record> ResultQuery<R>.awaitList(): List<R> = asFlow().toList()
