package tech.justdev.application.expense

import jakarta.inject.Singleton
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.domain.document.entity.ExpenseSupportingDocumentAttachment
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

class ExpenseSupportingDocumentAttachmentUnavailableException :
    RuntimeException("expense supporting document attachment is unavailable")

interface ReplaceExpenseSupportingDocumentUseCase {
    suspend operator fun invoke(command: ReplaceExpenseSupportingDocumentCommand)
}

@Singleton
class ReplaceExpenseSupportingDocumentUseCaseImpl(
    private val groupAccessPolicy: GroupAccessPolicy,
    private val expenseSupportingDocumentReplacement: ExpenseSupportingDocumentReplacement,
) : ReplaceExpenseSupportingDocumentUseCase {
    override suspend operator fun invoke(command: ReplaceExpenseSupportingDocumentCommand) {
        groupAccessPolicy.requireMember(command.group, command.requestedBy)
        expenseSupportingDocumentReplacement.inTransaction { scope ->
            val expense =
                scope.findExpense(command.expense, command.group)
                    ?: throw ExpenseNotFoundException(command.expense, command.group)
            expense.requireSupportingDocumentChangeBy(command.requestedBy)

            val replacedAttachment =
                scope.findCurrentAttachment(
                    sourceUploadIntent = command.replacedSourceUploadIntent,
                    expense = command.expense,
                    group = command.group,
                ) ?: throw ExpenseSupportingDocumentAttachmentUnavailableException()
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
            val replacementAttachment = ExpenseSupportingDocumentAttachment.replace(replacedAttachment, consumedReplacementIntent)

            scope.persist(consumedReplacementIntent, replacementAttachment)
        }
    }
}
