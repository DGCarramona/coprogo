package tech.justdev.application.document

import jakarta.inject.Singleton
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.domain.document.entity.DocumentMetadata
import tech.justdev.domain.document.entity.DocumentUploadIntent
import tech.justdev.domain.document.repository.DocumentUploadIntentRepository
import tech.justdev.domain.document.valueobject.DocumentFileName
import tech.justdev.domain.document.valueobject.DocumentStorageKey
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId
import java.time.Duration
import java.time.Instant

data class StartSupportingDocumentUploadCommand(
    val group: GroupId,
    val uploader: MemberEmail,
    val fileName: DocumentFileName,
    val metadata: DocumentMetadata,
    val createdAt: Instant,
    val validFor: Duration,
)

data class StartSupportingDocumentUploadResult(
    val intentId: DocumentUploadIntentId,
    val target: DocumentUploadTarget,
)

interface StartSupportingDocumentUploadUseCase {
    suspend operator fun invoke(command: StartSupportingDocumentUploadCommand): StartSupportingDocumentUploadResult
}

@Singleton
class StartSupportingDocumentUploadUseCaseImpl(
    private val groupAccessPolicy: GroupAccessPolicy,
    private val documentUploadIntentIdGenerator: DocumentUploadIntentIdGenerator,
    private val documentStorage: DocumentStorage,
    private val documentUploadIntentRepository: DocumentUploadIntentRepository,
) : StartSupportingDocumentUploadUseCase {
    override suspend operator fun invoke(command: StartSupportingDocumentUploadCommand): StartSupportingDocumentUploadResult {
        groupAccessPolicy.requireMember(command.group, command.uploader)

        val intentId = documentUploadIntentIdGenerator.next()

        val storageKey =
            intentId.let {
                DocumentStorageKey.of(
                    "groups/${command.group.toPrimitive()}/documents/${it.toPrimitive()}",
                )
            }

        val target =
            DocumentUploadRequest(
                key = storageKey,
                mediaType = command.metadata.mediaType,
                size = command.metadata.size,
                checksum = command.metadata.checksum,
                validFor = command.validFor,
            ).let { documentStorage.presignUpload(it) }

        return DocumentUploadIntent
            .create(
                id = intentId,
                group = command.group,
                uploader = command.uploader,
                storageKey = storageKey,
                fileName = command.fileName,
                expectedMetadata = command.metadata,
                createdAt = command.createdAt,
                expiresAt = target.expiresAt,
            ).also { documentUploadIntentRepository.persist(it) }
            .let {
                StartSupportingDocumentUploadResult(
                    intentId = it.id,
                    target = target,
                )
            }
    }
}
