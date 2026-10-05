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
    private val expenseSupportingDocumentDeletionPersistence: ExpenseSupportingDocumentDeletionPersistence,
) : DeleteExpenseSupportingDocumentUseCase {
    override suspend operator fun invoke(command: DeleteExpenseSupportingDocumentCommand) {
        groupAccessPolicy.requireMember(command.group, command.requestedBy)
        expenseSupportingDocumentDeletionPersistence.inTransaction { scope ->
            scope
                .findExpense(command.expense, command.group)
                .let { it ?: throw ExpenseNotFoundException(command.expense, command.group) }
                .deleteSupportingDocument(
                    sourceUploadIntent = command.sourceUploadIntent,
                    requestedBy = command.requestedBy,
                    deletedAt = command.deletedAt,
                ).let { scope.persist(it) }
        }
    }
}
