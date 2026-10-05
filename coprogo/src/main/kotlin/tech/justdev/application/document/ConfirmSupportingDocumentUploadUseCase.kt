package tech.justdev.application.document

import jakarta.inject.Singleton
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.domain.document.entity.DocumentUploadIntent
import tech.justdev.domain.document.repository.DocumentUploadIntentRepository
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId
import java.time.Instant

data class ConfirmSupportingDocumentUploadCommand(
    val group: GroupId,
    val uploader: MemberEmail,
    val intent: DocumentUploadIntentId,
    val verifiedAt: Instant,
)

class DocumentUploadIntentUnavailableException :
    RuntimeException(
        "document upload intent is unavailable",
    )

interface ConfirmSupportingDocumentUploadUseCase {
    suspend operator fun invoke(command: ConfirmSupportingDocumentUploadCommand)
}

@Singleton
class ConfirmSupportingDocumentUploadUseCaseImpl(
    private val groupAccessPolicy: GroupAccessPolicy,
    private val documentStorage: DocumentStorage,
    private val documentUploadIntentRepository: DocumentUploadIntentRepository,
) : ConfirmSupportingDocumentUploadUseCase {
    override suspend operator fun invoke(command: ConfirmSupportingDocumentUploadCommand) {
        groupAccessPolicy.requireMember(command.group, command.uploader)

        documentUploadIntentRepository
            .findPendingByIdAndGroupAndUploader(
                id = command.intent,
                group = command.group,
                uploader = command.uploader,
            ).let { inspectAndMarkReady(it, command.verifiedAt) }
            .let { documentUploadIntentRepository.persist(it) }
    }

    private suspend fun inspectAndMarkReady(
        intent: DocumentUploadIntent?,
        verifiedAt: Instant,
    ): DocumentUploadIntent =
        intent
            ?.let { pending ->
                documentStorage.inspect(pending.storageKey)?.let { metadata ->
                    try {
                        pending.markReady(metadata, verifiedAt)
                    } catch (_: IllegalArgumentException) {
                        null
                    }
                }
            }.let { it ?: throw DocumentUploadIntentUnavailableException() }
}
