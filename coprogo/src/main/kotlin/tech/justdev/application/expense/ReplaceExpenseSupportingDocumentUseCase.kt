package tech.justdev.application.expense

import jakarta.inject.Singleton
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId
import java.time.Instant

data class ReplaceExpenseSupportingDocumentCommand(
    val group: GroupId,
    val expense: ExpenseId,
    val replacedSourceUploadIntent: DocumentUploadIntentId,
    val replacementUploadIntent: DocumentUploadIntentId,
    val requestedBy: MemberEmail,
    val replacedAt: Instant,
)

interface ReplaceExpenseSupportingDocumentUseCase {
    suspend operator fun invoke(command: ReplaceExpenseSupportingDocumentCommand)
}

@Singleton
class ReplaceExpenseSupportingDocumentUseCaseImpl(
    private val groupAccessPolicy: GroupAccessPolicy,
    private val expenseSupportingDocumentReplacementPersistence: ExpenseSupportingDocumentReplacementPersistence,
) : ReplaceExpenseSupportingDocumentUseCase {
    override suspend operator fun invoke(command: ReplaceExpenseSupportingDocumentCommand) {
        groupAccessPolicy.requireMember(command.group, command.requestedBy)
        expenseSupportingDocumentReplacementPersistence.inTransaction { scope ->
            val expense =
                scope
                    .findExpense(command.expense, command.group)
                    .let { it ?: throw ExpenseNotFoundException(command.expense, command.group) }
                    .apply { requireSupportingDocumentChangeBy(command.requestedBy) }

            val replacementIntent =
                scope.findReadyReplacementUploadIntent(
                    id = command.replacementUploadIntent,
                    group = command.group,
                    uploader = command.requestedBy,
                ) ?: throw SupportingDocumentUploadIntentUnavailableException()

            val consumedReplacementIntent =
                try {
                    replacementIntent.consume(command.replacedAt)
                } catch (_: IllegalArgumentException) {
                    throw SupportingDocumentUploadIntentUnavailableException()
                }

            scope.persist(
                replacement =
                    expense.replaceSupportingDocument(
                        sourceUploadIntent = command.replacedSourceUploadIntent,
                        replacementIntent = consumedReplacementIntent,
                        requestedBy = command.requestedBy,
                    ),
                consumedReplacementIntent = consumedReplacementIntent,
            )
        }
    }
}
