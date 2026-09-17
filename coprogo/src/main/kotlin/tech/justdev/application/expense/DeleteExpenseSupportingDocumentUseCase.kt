package tech.justdev.application.expense

import jakarta.inject.Singleton
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId
import java.time.Instant

data class DeleteExpenseSupportingDocumentCommand(
    val group: GroupId,
    val expense: ExpenseId,
    val sourceUploadIntent: DocumentUploadIntentId,
    val requestedBy: MemberEmail,
    val deletedAt: Instant,
)

interface DeleteExpenseSupportingDocumentUseCase {
    suspend operator fun invoke(command: DeleteExpenseSupportingDocumentCommand)
}

@Singleton
class DeleteExpenseSupportingDocumentUseCaseImpl(
    private val groupAccessPolicy: GroupAccessPolicy,
    private val expenseSupportingDocumentDeletion: ExpenseSupportingDocumentDeletion,
) : DeleteExpenseSupportingDocumentUseCase {
    override suspend operator fun invoke(command: DeleteExpenseSupportingDocumentCommand) {
        groupAccessPolicy.requireMember(command.group, command.requestedBy)
        expenseSupportingDocumentDeletion.inTransaction { scope ->
            val expense =
                scope.findExpense(command.expense, command.group)
                    ?: throw ExpenseNotFoundException(command.expense, command.group)
            expense.requireSupportingDocumentChangeBy(command.requestedBy)

            val attachment =
                scope.findCurrentAttachment(
                    sourceUploadIntent = command.sourceUploadIntent,
                    expense = command.expense,
                    group = command.group,
                ) ?: throw ExpenseSupportingDocumentAttachmentUnavailableException()

            scope.persist(attachment.delete(command.requestedBy, command.deletedAt))
        }
    }
}
