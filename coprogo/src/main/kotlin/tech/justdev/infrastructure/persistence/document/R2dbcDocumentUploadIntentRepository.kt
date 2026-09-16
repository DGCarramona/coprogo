package tech.justdev.infrastructure.persistence.document

import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.jooq.Condition
import org.jooq.Record13
import org.jooq.impl.DSL
import tech.justdev.domain.document.entity.DocumentMetadata
import tech.justdev.domain.document.entity.DocumentUploadIntent
import tech.justdev.domain.document.entity.DocumentUploadIntentStatus
import tech.justdev.domain.document.repository.DocumentUploadIntentRepository
import tech.justdev.domain.document.valueobject.DocumentFileName
import tech.justdev.domain.document.valueobject.DocumentMediaType
import tech.justdev.domain.document.valueobject.DocumentSha256
import tech.justdev.domain.document.valueobject.DocumentSize
import tech.justdev.domain.document.valueobject.DocumentStorageKey
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.infrastructure.persistence.jooq.Tables.DOCUMENT_UPLOAD_INTENTS
import tech.justdev.infrastructure.persistence.jooq.dsl
import tech.justdev.infrastructure.persistence.jooq.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

private typealias UploadIntentRecord =
    Record13<
        UUID,
        UUID,
        String,
        String,
        String,
        String,
        Long,
        String,
        OffsetDateTime,
        OffsetDateTime,
        String,
        OffsetDateTime?,
        OffsetDateTime?,
    >

@Singleton
class R2dbcDocumentUploadIntentRepository(
    @param:Named("default")
    private val connectionFactory: ConnectionFactory,
) : DocumentUploadIntentRepository {
    override suspend fun persist(intent: DocumentUploadIntent) = persistAll(listOf(intent))

    override suspend fun persistAll(intents: List<DocumentUploadIntent>) {
        if (intents.isEmpty()) return
        require(intents.map(DocumentUploadIntent::id).distinct().size == intents.size) {
            "document upload intent identifiers must be unique"
        }

        connectionFactory.transaction {
            val rows =
                intents.map { intent ->
                    val state = intent.status.toPersistenceState()
                    DSL.row(
                        intent.id.toPrimitive(),
                        intent.group.toPrimitive(),
                        intent.uploader.toPrimitive(),
                        intent.storageKey.toPrimitive(),
                        intent.fileName.toPrimitive(),
                        intent.expectedMetadata.mediaType.toPrimitive(),
                        intent.expectedMetadata.size.toBytes(),
                        intent.expectedMetadata.checksum.toBase64(),
                        intent.createdAt.atOffset(ZoneOffset.UTC),
                        intent.expiresAt.atOffset(ZoneOffset.UTC),
                        state.status.name,
                        state.readyAt?.atOffset(ZoneOffset.UTC),
                        state.consumedAt?.atOffset(ZoneOffset.UTC),
                    )
                }
            val dsl = connectionFactory.dsl()
            val upsert =
                dsl
                    .insertInto(
                        DOCUMENT_UPLOAD_INTENTS,
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
                    ).valuesOfRows(rows)
                    .onConflict(DOCUMENT_UPLOAD_INTENTS.ID)
                    .doUpdate()
                    .set(DOCUMENT_UPLOAD_INTENTS.STATUS, DSL.excluded(DOCUMENT_UPLOAD_INTENTS.STATUS))
                    .set(DOCUMENT_UPLOAD_INTENTS.READY_AT, DSL.excluded(DOCUMENT_UPLOAD_INTENTS.READY_AT))
                    .set(DOCUMENT_UPLOAD_INTENTS.CONSUMED_AT, DSL.excluded(DOCUMENT_UPLOAD_INTENTS.CONSUMED_AT))
                    .where(immutableFieldsMatchExcluded())
                    .and(allowedCurrentStateForExcluded())
                    .returningResult(DOCUMENT_UPLOAD_INTENTS.ID)

            val persistedCountQuery =
                dsl
                    .with("persisted", "id")
                    .`as`(upsert)
                    .selectCount()
                    .from("persisted")
            val persistedCount = persistedCountQuery.awaitSingle().value1()

            check(persistedCount == intents.size) {
                documentUploadIntentPersistenceFailureMessage(intents.size)
            }
        }
    }

    override suspend fun findByIdAndGroup(
        id: DocumentUploadIntentId,
        group: GroupId,
    ): DocumentUploadIntent? =
        connectionFactory
            .dsl()
            .select(
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
            ).from(DOCUMENT_UPLOAD_INTENTS)
            .where(DOCUMENT_UPLOAD_INTENTS.ID.eq(id.toPrimitive()))
            .and(DOCUMENT_UPLOAD_INTENTS.GROUP.eq(group.toPrimitive()))
            .awaitFirstOrNull()
            ?.toDomain()

    override suspend fun findReadyByIdsAndGroupAndUploader(
        ids: Set<DocumentUploadIntentId>,
        group: GroupId,
        uploader: MemberEmail,
    ): List<DocumentUploadIntent> {
        if (ids.isEmpty()) return emptyList()

        return connectionFactory
            .dsl()
            .select(
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
            ).from(DOCUMENT_UPLOAD_INTENTS)
            .where(DOCUMENT_UPLOAD_INTENTS.ID.`in`(ids.map { it.toPrimitive() }))
            .and(DOCUMENT_UPLOAD_INTENTS.GROUP.eq(group.toPrimitive()))
            .and(DOCUMENT_UPLOAD_INTENTS.UPLOADER.eq(uploader.toPrimitive()))
            .and(DOCUMENT_UPLOAD_INTENTS.STATUS.eq(PersistenceStatus.READY.name))
            .orderBy(DOCUMENT_UPLOAD_INTENTS.ID.asc())
            .asFlow()
            .toList()
            .map { it.toDomain() }
    }

    internal suspend fun findReadyByIdsAndGroupAndUploaderForUpdate(
        ids: Set<DocumentUploadIntentId>,
        group: GroupId,
        uploader: MemberEmail,
    ): List<DocumentUploadIntent> {
        if (ids.isEmpty()) return emptyList()

        return connectionFactory
            .dsl()
            .select(
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
            ).from(DOCUMENT_UPLOAD_INTENTS)
            .where(DOCUMENT_UPLOAD_INTENTS.ID.`in`(ids.map { it.toPrimitive() }))
            .and(DOCUMENT_UPLOAD_INTENTS.GROUP.eq(group.toPrimitive()))
            .and(DOCUMENT_UPLOAD_INTENTS.UPLOADER.eq(uploader.toPrimitive()))
            .and(DOCUMENT_UPLOAD_INTENTS.STATUS.eq(PersistenceStatus.READY.name))
            .orderBy(DOCUMENT_UPLOAD_INTENTS.ID.asc())
            .forUpdate()
            .asFlow()
            .toList()
            .map { it.toDomain() }
    }
}

private data class PersistenceState(
    val status: PersistenceStatus,
    val readyAt: java.time.Instant?,
    val consumedAt: java.time.Instant?,
)

private enum class PersistenceStatus {
    PENDING,
    READY,
    CONSUMED,
}

private fun DocumentUploadIntentStatus.toPersistenceState(): PersistenceState =
    when (this) {
        DocumentUploadIntentStatus.Pending -> PersistenceState(PersistenceStatus.PENDING, null, null)
        is DocumentUploadIntentStatus.Ready -> PersistenceState(PersistenceStatus.READY, verifiedAt, null)
        is DocumentUploadIntentStatus.Consumed -> PersistenceState(PersistenceStatus.CONSUMED, verifiedAt, consumedAt)
    }

private fun immutableFieldsMatchExcluded(): Condition =
    DOCUMENT_UPLOAD_INTENTS.GROUP
        .eq(DSL.excluded(DOCUMENT_UPLOAD_INTENTS.GROUP))
        .and(DOCUMENT_UPLOAD_INTENTS.UPLOADER.eq(DSL.excluded(DOCUMENT_UPLOAD_INTENTS.UPLOADER)))
        .and(DOCUMENT_UPLOAD_INTENTS.STORAGE_KEY.eq(DSL.excluded(DOCUMENT_UPLOAD_INTENTS.STORAGE_KEY)))
        .and(DOCUMENT_UPLOAD_INTENTS.FILE_NAME.eq(DSL.excluded(DOCUMENT_UPLOAD_INTENTS.FILE_NAME)))
        .and(DOCUMENT_UPLOAD_INTENTS.MEDIA_TYPE.eq(DSL.excluded(DOCUMENT_UPLOAD_INTENTS.MEDIA_TYPE)))
        .and(DOCUMENT_UPLOAD_INTENTS.EXPECTED_SIZE.eq(DSL.excluded(DOCUMENT_UPLOAD_INTENTS.EXPECTED_SIZE)))
        .and(DOCUMENT_UPLOAD_INTENTS.EXPECTED_SHA256.eq(DSL.excluded(DOCUMENT_UPLOAD_INTENTS.EXPECTED_SHA256)))
        .and(DOCUMENT_UPLOAD_INTENTS.CREATED_AT.eq(DSL.excluded(DOCUMENT_UPLOAD_INTENTS.CREATED_AT)))
        .and(DOCUMENT_UPLOAD_INTENTS.EXPIRES_AT.eq(DSL.excluded(DOCUMENT_UPLOAD_INTENTS.EXPIRES_AT)))

private fun allowedCurrentStateForExcluded(): Condition {
    val pending =
        DOCUMENT_UPLOAD_INTENTS.STATUS
            .eq(PersistenceStatus.PENDING.name)
            .and(DOCUMENT_UPLOAD_INTENTS.READY_AT.isNull)
            .and(DOCUMENT_UPLOAD_INTENTS.CONSUMED_AT.isNull)
    val readyWithExcludedTimestamp =
        DOCUMENT_UPLOAD_INTENTS.STATUS
            .eq(PersistenceStatus.READY.name)
            .and(DOCUMENT_UPLOAD_INTENTS.READY_AT.eq(DSL.excluded(DOCUMENT_UPLOAD_INTENTS.READY_AT)))
            .and(DOCUMENT_UPLOAD_INTENTS.CONSUMED_AT.isNull)
    val requestedStatus = DSL.excluded(DOCUMENT_UPLOAD_INTENTS.STATUS)

    return requestedStatus
        .eq(PersistenceStatus.PENDING.name)
        .and(pending)
        .or(requestedStatus.eq(PersistenceStatus.READY.name).and(pending.or(readyWithExcludedTimestamp)))
        .or(requestedStatus.eq(PersistenceStatus.CONSUMED.name).and(readyWithExcludedTimestamp))
}

private fun documentUploadIntentPersistenceFailureMessage(requestedCount: Int): String =
    if (requestedCount == 1) {
        "document upload intent persistence must affect exactly one row"
    } else {
        "document upload intent persistence must affect exactly the requested rows"
    }

private fun UploadIntentRecord.toDomain(): DocumentUploadIntent =
    DocumentUploadIntent.restore(
        id = DocumentUploadIntentId(value1()),
        group = GroupId(value2()),
        uploader = MemberEmail.of(value3()),
        storageKey = DocumentStorageKey.of(value4()),
        fileName = DocumentFileName.of(value5()),
        expectedMetadata =
            DocumentMetadata(
                mediaType = DocumentMediaType.of(value6()),
                size = DocumentSize.ofBytes(value7()),
                checksum = DocumentSha256.fromBase64(value8()),
            ),
        createdAt = value9().toInstant(),
        expiresAt = value10().toInstant(),
        status = value11().toDomainStatus(value12(), value13()),
    )

private fun String.toDomainStatus(
    readyAt: OffsetDateTime?,
    consumedAt: OffsetDateTime?,
): DocumentUploadIntentStatus =
    when (this) {
        "PENDING" -> {
            require(readyAt == null && consumedAt == null) { "pending document upload intent must not have transition timestamps" }
            DocumentUploadIntentStatus.Pending
        }

        "READY" -> {
            require(consumedAt == null) { "ready document upload intent must not have a consumption timestamp" }
            DocumentUploadIntentStatus.Ready(
                requireNotNull(readyAt) { "ready document upload intent must have a verification timestamp" }.toInstant(),
            )
        }

        "CONSUMED" -> {
            DocumentUploadIntentStatus.Consumed(
                verifiedAt = requireNotNull(readyAt) { "consumed document upload intent must have a verification timestamp" }.toInstant(),
                consumedAt = requireNotNull(consumedAt) { "consumed document upload intent must have a consumption timestamp" }.toInstant(),
            )
        }

        else -> {
            throw IllegalArgumentException("unsupported document upload intent status: $this")
        }
    }
