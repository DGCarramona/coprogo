package tech.justdev.infrastructure.persistence.document

import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.jooq.Condition
import org.jooq.Record13
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
    override suspend fun persist(intent: DocumentUploadIntent) {
        val state = intent.status.toPersistenceState()
        val affectedRows =
            connectionFactory
                .dsl()
                .insertInto(DOCUMENT_UPLOAD_INTENTS)
                .columns(
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
                ).values(
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
                ).onConflict(DOCUMENT_UPLOAD_INTENTS.ID)
                .doUpdate()
                .set(DOCUMENT_UPLOAD_INTENTS.STATUS, state.status.name)
                .set(DOCUMENT_UPLOAD_INTENTS.READY_AT, state.readyAt?.atOffset(ZoneOffset.UTC))
                .set(DOCUMENT_UPLOAD_INTENTS.CONSUMED_AT, state.consumedAt?.atOffset(ZoneOffset.UTC))
                .where(DOCUMENT_UPLOAD_INTENTS.GROUP.eq(intent.group.toPrimitive()))
                .and(DOCUMENT_UPLOAD_INTENTS.UPLOADER.eq(intent.uploader.toPrimitive()))
                .and(DOCUMENT_UPLOAD_INTENTS.STORAGE_KEY.eq(intent.storageKey.toPrimitive()))
                .and(DOCUMENT_UPLOAD_INTENTS.FILE_NAME.eq(intent.fileName.toPrimitive()))
                .and(DOCUMENT_UPLOAD_INTENTS.MEDIA_TYPE.eq(intent.expectedMetadata.mediaType.toPrimitive()))
                .and(DOCUMENT_UPLOAD_INTENTS.EXPECTED_SIZE.eq(intent.expectedMetadata.size.toBytes()))
                .and(DOCUMENT_UPLOAD_INTENTS.EXPECTED_SHA256.eq(intent.expectedMetadata.checksum.toBase64()))
                .and(DOCUMENT_UPLOAD_INTENTS.CREATED_AT.eq(intent.createdAt.atOffset(ZoneOffset.UTC)))
                .and(DOCUMENT_UPLOAD_INTENTS.EXPIRES_AT.eq(intent.expiresAt.atOffset(ZoneOffset.UTC)))
                .and(state.allowedCurrentState())
                .awaitSingle()
        check(affectedRows == 1) { "document upload intent persistence must affect exactly one row" }
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

private fun PersistenceState.allowedCurrentState(): Condition {
    val pending =
        DOCUMENT_UPLOAD_INTENTS.STATUS
            .eq(PersistenceStatus.PENDING.name)
            .and(DOCUMENT_UPLOAD_INTENTS.READY_AT.isNull)
            .and(DOCUMENT_UPLOAD_INTENTS.CONSUMED_AT.isNull)

    return when (status) {
        PersistenceStatus.PENDING -> pending
        PersistenceStatus.READY -> pending.or(currentReadyState())
        PersistenceStatus.CONSUMED -> pending.or(currentReadyState()).or(currentConsumedState())
    }
}

private fun PersistenceState.currentReadyState(): Condition =
    DOCUMENT_UPLOAD_INTENTS.STATUS
        .eq(PersistenceStatus.READY.name)
        .and(DOCUMENT_UPLOAD_INTENTS.READY_AT.eq(requireNotNull(readyAt).atOffset(ZoneOffset.UTC)))
        .and(DOCUMENT_UPLOAD_INTENTS.CONSUMED_AT.isNull)

private fun PersistenceState.currentConsumedState(): Condition =
    DOCUMENT_UPLOAD_INTENTS.STATUS
        .eq(PersistenceStatus.CONSUMED.name)
        .and(DOCUMENT_UPLOAD_INTENTS.READY_AT.eq(requireNotNull(readyAt).atOffset(ZoneOffset.UTC)))
        .and(DOCUMENT_UPLOAD_INTENTS.CONSUMED_AT.eq(requireNotNull(consumedAt).atOffset(ZoneOffset.UTC)))

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
