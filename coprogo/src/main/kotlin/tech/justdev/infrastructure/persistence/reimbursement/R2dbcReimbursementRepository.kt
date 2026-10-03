package tech.justdev.infrastructure.persistence.reimbursement

import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.exception.IntegrityConstraintViolationException
import org.jooq.impl.DSL
import tech.justdev.domain.document.entity.DocumentMetadata
import tech.justdev.domain.document.valueobject.DocumentFileName
import tech.justdev.domain.document.valueobject.DocumentMediaType
import tech.justdev.domain.document.valueobject.DocumentSha256
import tech.justdev.domain.document.valueobject.DocumentSize
import tech.justdev.domain.document.valueobject.DocumentStorageKey
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.reimbursement.entity.Reimbursement
import tech.justdev.domain.reimbursement.entity.ReimbursementSupportingDocument
import tech.justdev.domain.reimbursement.repository.ReimbursementRepository
import tech.justdev.domain.reimbursement.valueobject.ReimbursementId
import tech.justdev.domain.reimbursement.valueobject.ReimbursementRejectionReason
import tech.justdev.domain.reimbursement.valueobject.ReimbursementStatus
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.infrastructure.persistence.jooq.Tables.DOCUMENT_UPLOAD_INTENTS
import tech.justdev.infrastructure.persistence.jooq.Tables.REIMBURSEMENTS
import tech.justdev.infrastructure.persistence.jooq.Tables.REIMBURSEMENT_REVIEW_DECISIONS
import tech.justdev.infrastructure.persistence.jooq.Tables.REIMBURSEMENT_SUPPORTING_DOCUMENTS
import tech.justdev.infrastructure.persistence.jooq.Tables.SUPPORTING_DOCUMENT_ATTACHMENTS
import tech.justdev.infrastructure.persistence.jooq.awaitList
import tech.justdev.infrastructure.persistence.jooq.dsl
import tech.justdev.infrastructure.persistence.jooq.enums.ReimbursementReviewDecision
import tech.justdev.infrastructure.persistence.jooq.enums.SupportingDocumentAttachmentType
import tech.justdev.infrastructure.persistence.jooq.transaction
import java.time.ZoneOffset

private const val IMMUTABLE_HISTORY_CONFLICT = "reimbursement persistence conflicts with its immutable history"

@Singleton
open class R2dbcReimbursementRepository(
    @param:Named("default")
    private val connectionFactory: ConnectionFactory,
) : ReimbursementRepository {
    override suspend fun findByIdAndGroup(
        id: ReimbursementId,
        group: GroupId,
    ): Reimbursement? {
        val dsl = connectionFactory.dsl()
        val record =
            dsl
                .select(
                    REIMBURSEMENTS.ID,
                    REIMBURSEMENTS.GROUP,
                    REIMBURSEMENTS.PAID_BY,
                    REIMBURSEMENTS.RECEIVED_BY,
                    REIMBURSEMENTS.AMOUNT,
                    REIMBURSEMENTS.REIMBURSED_AT,
                    REIMBURSEMENTS.DECLARED_BY,
                    REIMBURSEMENTS.DECLARED_AT,
                    REIMBURSEMENT_REVIEW_DECISIONS.DECISION,
                    REIMBURSEMENT_REVIEW_DECISIONS.DECIDED_AT,
                    REIMBURSEMENT_REVIEW_DECISIONS.REJECTION_REASON,
                ).from(REIMBURSEMENTS)
                .leftJoin(REIMBURSEMENT_REVIEW_DECISIONS)
                .on(REIMBURSEMENT_REVIEW_DECISIONS.REIMBURSEMENT.eq(REIMBURSEMENTS.ID))
                .and(REIMBURSEMENT_REVIEW_DECISIONS.GROUP.eq(REIMBURSEMENTS.GROUP))
                .where(REIMBURSEMENTS.ID.eq(id.toPrimitive()))
                .and(REIMBURSEMENTS.GROUP.eq(group.toPrimitive()))
                .awaitFirstOrNull()
                ?: return null

        return record.toDomain(dsl.findSupportingDocuments(id, group))
    }

    override suspend fun persist(reimbursement: Reimbursement) {
        try {
            connectionFactory.transaction {
                val dsl = connectionFactory.dsl()
                dsl.persistRoot(reimbursement)
                dsl.persistSupportingDocuments(reimbursement)
                dsl.persistReviewDecision(reimbursement)

                check(findByIdAndGroup(reimbursement.id, reimbursement.group)?.hasSameStateAs(reimbursement) == true) {
                    IMMUTABLE_HISTORY_CONFLICT
                }
            }
        } catch (exception: IntegrityConstraintViolationException) {
            throw IllegalStateException(IMMUTABLE_HISTORY_CONFLICT, exception)
        }
    }
}

private suspend fun DSLContext.persistRoot(reimbursement: Reimbursement) {
    insertInto(REIMBURSEMENTS)
        .columns(
            REIMBURSEMENTS.ID,
            REIMBURSEMENTS.GROUP,
            REIMBURSEMENTS.PAID_BY,
            REIMBURSEMENTS.RECEIVED_BY,
            REIMBURSEMENTS.AMOUNT,
            REIMBURSEMENTS.REIMBURSED_AT,
            REIMBURSEMENTS.DECLARED_BY,
            REIMBURSEMENTS.DECLARED_AT,
        ).values(
            reimbursement.id.toPrimitive(),
            reimbursement.group.toPrimitive(),
            reimbursement.paidBy.toPrimitive(),
            reimbursement.receivedBy.toPrimitive(),
            reimbursement.amount.inCents(),
            reimbursement.reimbursedAt.atOffset(ZoneOffset.UTC),
            reimbursement.declaredBy.toPrimitive(),
            reimbursement.declaredAt.atOffset(ZoneOffset.UTC),
        ).onConflict(REIMBURSEMENTS.ID)
        .doNothing()
        .awaitFirstOrNull()
}

private suspend fun DSLContext.persistSupportingDocuments(reimbursement: Reimbursement) {
    if (reimbursement.supportingDocuments.isEmpty()) return

    insertInto(SUPPORTING_DOCUMENT_ATTACHMENTS)
        .columns(
            SUPPORTING_DOCUMENT_ATTACHMENTS.SOURCE_UPLOAD_INTENT,
            SUPPORTING_DOCUMENT_ATTACHMENTS.GROUP,
            SUPPORTING_DOCUMENT_ATTACHMENTS.SOURCE_STATUS,
            SUPPORTING_DOCUMENT_ATTACHMENTS.TYPE,
            SUPPORTING_DOCUMENT_ATTACHMENTS.DELETED_BY,
            SUPPORTING_DOCUMENT_ATTACHMENTS.DELETED_AT,
        ).valuesOfRows(
            reimbursement.supportingDocuments.map { document ->
                DSL.row(
                    document.sourceUploadIntent.toPrimitive(),
                    document.group.toPrimitive(),
                    "CONSUMED",
                    SupportingDocumentAttachmentType.REIMBURSEMENT,
                    null as String?,
                    null as java.time.OffsetDateTime?,
                )
            },
        ).onConflict(SUPPORTING_DOCUMENT_ATTACHMENTS.SOURCE_UPLOAD_INTENT)
        .doNothing()
        .awaitFirstOrNull()

    insertInto(REIMBURSEMENT_SUPPORTING_DOCUMENTS)
        .columns(
            REIMBURSEMENT_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT,
            REIMBURSEMENT_SUPPORTING_DOCUMENTS.GROUP,
            REIMBURSEMENT_SUPPORTING_DOCUMENTS.SOURCE_STATUS,
            REIMBURSEMENT_SUPPORTING_DOCUMENTS.TYPE,
            REIMBURSEMENT_SUPPORTING_DOCUMENTS.REIMBURSEMENT,
            REIMBURSEMENT_SUPPORTING_DOCUMENTS.UPLOADER,
        ).valuesOfRows(
            reimbursement.supportingDocuments.map { document ->
                DSL.row(
                    document.sourceUploadIntent.toPrimitive(),
                    document.group.toPrimitive(),
                    "CONSUMED",
                    SupportingDocumentAttachmentType.REIMBURSEMENT,
                    reimbursement.id.toPrimitive(),
                    document.uploader.toPrimitive(),
                )
            },
        ).onConflict(REIMBURSEMENT_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT)
        .doNothing()
        .awaitFirstOrNull()
}

private suspend fun DSLContext.persistReviewDecision(reimbursement: Reimbursement) {
    val status = reimbursement.status
    val isDirectAcceptance = status is ReimbursementStatus.Accepted && reimbursement.supportingDocuments.isEmpty()
    if (status == ReimbursementStatus.PendingReview || isDirectAcceptance) {
        return
    }

    val decision =
        when (status) {
            ReimbursementStatus.PendingReview -> error("pending reimbursement has no review decision")
            is ReimbursementStatus.Accepted -> ReimbursementReviewDecision.ACCEPTED
            is ReimbursementStatus.Rejected -> ReimbursementReviewDecision.REJECTED
        }
    val decidedAt =
        when (status) {
            ReimbursementStatus.PendingReview -> error("pending reimbursement has no review decision")
            is ReimbursementStatus.Accepted -> status.acceptedAt
            is ReimbursementStatus.Rejected -> status.decidedAt
        }
    val rejectionReason =
        (status as? ReimbursementStatus.Rejected)
            ?.reason
            ?.toPrimitive()

    insertInto(REIMBURSEMENT_REVIEW_DECISIONS)
        .columns(
            REIMBURSEMENT_REVIEW_DECISIONS.REIMBURSEMENT,
            REIMBURSEMENT_REVIEW_DECISIONS.GROUP,
            REIMBURSEMENT_REVIEW_DECISIONS.REVIEWED_BY,
            REIMBURSEMENT_REVIEW_DECISIONS.DECISION,
            REIMBURSEMENT_REVIEW_DECISIONS.DECIDED_AT,
            REIMBURSEMENT_REVIEW_DECISIONS.REJECTION_REASON,
        ).values(
            reimbursement.id.toPrimitive(),
            reimbursement.group.toPrimitive(),
            reimbursement.receivedBy.toPrimitive(),
            decision,
            decidedAt.atOffset(ZoneOffset.UTC),
            rejectionReason,
        ).onConflict(REIMBURSEMENT_REVIEW_DECISIONS.REIMBURSEMENT)
        .doNothing()
        .awaitFirstOrNull()
}

private suspend fun DSLContext.findSupportingDocuments(
    reimbursement: ReimbursementId,
    group: GroupId,
): List<ReimbursementSupportingDocument> =
    select(
        DOCUMENT_UPLOAD_INTENTS.ID,
        DOCUMENT_UPLOAD_INTENTS.GROUP,
        DOCUMENT_UPLOAD_INTENTS.UPLOADER,
        DOCUMENT_UPLOAD_INTENTS.STORAGE_KEY,
        DOCUMENT_UPLOAD_INTENTS.FILE_NAME,
        DOCUMENT_UPLOAD_INTENTS.MEDIA_TYPE,
        DOCUMENT_UPLOAD_INTENTS.EXPECTED_SIZE,
        DOCUMENT_UPLOAD_INTENTS.EXPECTED_SHA256,
        DOCUMENT_UPLOAD_INTENTS.STATUS,
        DOCUMENT_UPLOAD_INTENTS.CONSUMED_AT,
    ).from(REIMBURSEMENT_SUPPORTING_DOCUMENTS)
        .join(DOCUMENT_UPLOAD_INTENTS)
        .on(DOCUMENT_UPLOAD_INTENTS.ID.eq(REIMBURSEMENT_SUPPORTING_DOCUMENTS.SOURCE_UPLOAD_INTENT))
        .and(DOCUMENT_UPLOAD_INTENTS.GROUP.eq(REIMBURSEMENT_SUPPORTING_DOCUMENTS.GROUP))
        .where(REIMBURSEMENT_SUPPORTING_DOCUMENTS.REIMBURSEMENT.eq(reimbursement.toPrimitive()))
        .and(REIMBURSEMENT_SUPPORTING_DOCUMENTS.GROUP.eq(group.toPrimitive()))
        .orderBy(DOCUMENT_UPLOAD_INTENTS.CONSUMED_AT, DOCUMENT_UPLOAD_INTENTS.ID)
        .awaitList()
        .map(Record::toSupportingDocument)

private fun Record.toSupportingDocument(): ReimbursementSupportingDocument {
    require(get(DOCUMENT_UPLOAD_INTENTS.STATUS) == "CONSUMED") {
        "reimbursement supporting document upload intent must be consumed"
    }

    return ReimbursementSupportingDocument.restore(
        sourceUploadIntent = DocumentUploadIntentId(get(DOCUMENT_UPLOAD_INTENTS.ID)),
        group = GroupId(get(DOCUMENT_UPLOAD_INTENTS.GROUP)),
        uploader = MemberEmail.of(get(DOCUMENT_UPLOAD_INTENTS.UPLOADER)),
        storageKey = DocumentStorageKey.of(get(DOCUMENT_UPLOAD_INTENTS.STORAGE_KEY)),
        fileName = DocumentFileName.of(get(DOCUMENT_UPLOAD_INTENTS.FILE_NAME)),
        metadata =
            DocumentMetadata(
                mediaType = DocumentMediaType.of(get(DOCUMENT_UPLOAD_INTENTS.MEDIA_TYPE)),
                size = DocumentSize.ofBytes(get(DOCUMENT_UPLOAD_INTENTS.EXPECTED_SIZE)),
                checksum = DocumentSha256.fromBase64(get(DOCUMENT_UPLOAD_INTENTS.EXPECTED_SHA256)),
            ),
        attachedAt = requireNotNull(get(DOCUMENT_UPLOAD_INTENTS.CONSUMED_AT)).toInstant(),
    )
}

private fun Record.toDomain(supportingDocuments: List<ReimbursementSupportingDocument>): Reimbursement {
    val declaredBy = MemberEmail.of(get(REIMBURSEMENTS.DECLARED_BY))
    val receivedBy = MemberEmail.of(get(REIMBURSEMENTS.RECEIVED_BY))
    val declaredAt = get(REIMBURSEMENTS.DECLARED_AT).toInstant()

    return Reimbursement.restore(
        id = ReimbursementId(get(REIMBURSEMENTS.ID)),
        group = GroupId(get(REIMBURSEMENTS.GROUP)),
        paidBy = MemberEmail.of(get(REIMBURSEMENTS.PAID_BY)),
        receivedBy = receivedBy,
        amount = MoneyAmount.ofCents(get(REIMBURSEMENTS.AMOUNT)),
        reimbursedAt = get(REIMBURSEMENTS.REIMBURSED_AT).toInstant(),
        declaredBy = declaredBy,
        declaredAt = declaredAt,
        status = toStatus(declaredBy, receivedBy, declaredAt),
        supportingDocuments = supportingDocuments,
    )
}

private fun Record.toStatus(
    declaredBy: MemberEmail,
    receivedBy: MemberEmail,
    declaredAt: java.time.Instant,
): ReimbursementStatus =
    when (get(REIMBURSEMENT_REVIEW_DECISIONS.DECISION)) {
        ReimbursementReviewDecision.ACCEPTED ->
            ReimbursementStatus.Accepted(requireNotNull(get(REIMBURSEMENT_REVIEW_DECISIONS.DECIDED_AT)).toInstant())
        ReimbursementReviewDecision.REJECTED ->
            ReimbursementStatus.Rejected(
                decidedAt = requireNotNull(get(REIMBURSEMENT_REVIEW_DECISIONS.DECIDED_AT)).toInstant(),
                reason = get(REIMBURSEMENT_REVIEW_DECISIONS.REJECTION_REASON)?.let(ReimbursementRejectionReason::of),
            )
        null ->
            if (declaredBy == receivedBy) {
                ReimbursementStatus.Accepted(declaredAt)
            } else {
                ReimbursementStatus.PendingReview
            }
    }

private fun Reimbursement.hasSameStateAs(expected: Reimbursement): Boolean =
    id == expected.id &&
        group == expected.group &&
        paidBy == expected.paidBy &&
        receivedBy == expected.receivedBy &&
        amount == expected.amount &&
        reimbursedAt == expected.reimbursedAt &&
        declaredBy == expected.declaredBy &&
        declaredAt == expected.declaredAt &&
        status == expected.status &&
        supportingDocuments == expected.supportingDocuments
