package tech.justdev.application.reimbursement

import jakarta.inject.Singleton
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.domain.document.entity.DocumentUploadIntent
import tech.justdev.domain.document.repository.DocumentUploadIntentRepository
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.reimbursement.entity.Reimbursement
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import java.time.Instant

data class DeclareDocumentedReimbursementCommand(
    val group: GroupId,
    val paidBy: MemberEmail,
    val receivedBy: MemberEmail,
    val amountInCents: Long,
    val reimbursedAt: Instant,
    val declaredAt: Instant,
    val supportingDocumentUploadIntents: Set<DocumentUploadIntentId>,
)

class SupportingDocumentUploadIntentUnavailableException : RuntimeException("supporting document upload intent is unavailable")

interface DeclareDocumentedReimbursementUseCase {
    suspend operator fun invoke(command: DeclareDocumentedReimbursementCommand)
}

@Singleton
class DeclareDocumentedReimbursementUseCaseImpl(
    private val groupAccessPolicy: GroupAccessPolicy,
    private val reimbursementIdGenerator: ReimbursementIdGenerator,
    private val documentUploadIntentRepository: DocumentUploadIntentRepository,
    private val reimbursementDeclarationPersistence: ReimbursementDeclarationPersistence,
) : DeclareDocumentedReimbursementUseCase {
    override suspend operator fun invoke(command: DeclareDocumentedReimbursementCommand) {
        val group = groupAccessPolicy.requireMember(command.group, command.paidBy)
        require(group.contains(command.receivedBy)) {
            "reimbursement receiver ${command.receivedBy.toPrimitive()} is not part of group ${command.group.toPrimitive()}"
        }
        if (command.supportingDocumentUploadIntents.isEmpty()) {
            throw SupportingDocumentUploadIntentUnavailableException()
        }

        consumeUploadIntents(command)
            .let {
                DocumentedReimbursementDeclaration
                    .from(
                        Reimbursement.declareWithSupportingDocuments(
                            id = reimbursementIdGenerator.next(),
                            group = command.group,
                            paidBy = command.paidBy,
                            receivedBy = command.receivedBy,
                            amount = MoneyAmount.ofCents(command.amountInCents),
                            reimbursedAt = command.reimbursedAt,
                            declaredBy = command.paidBy,
                            declaredAt = command.declaredAt,
                            supportingDocumentUploadIntents = it,
                        ),
                        it,
                    )
            }.let { declaration -> reimbursementDeclarationPersistence.persist(declaration) }
    }

    private suspend fun consumeUploadIntents(command: DeclareDocumentedReimbursementCommand): List<DocumentUploadIntent> =
        documentUploadIntentRepository
            .findReadyByIdsAndGroupAndUploader(
                ids = command.supportingDocumentUploadIntents,
                group = command.group,
                uploader = command.paidBy,
            ).also { intents ->
                if (intents.map(DocumentUploadIntent::id).toSet() != command.supportingDocumentUploadIntents) {
                    throw SupportingDocumentUploadIntentUnavailableException()
                }
            }.sortedBy { intent -> intent.id.toPrimitive() }
            .map { intent ->
                try {
                    intent.consume(command.declaredAt)
                } catch (_: IllegalArgumentException) {
                    throw SupportingDocumentUploadIntentUnavailableException()
                }
            }
}
