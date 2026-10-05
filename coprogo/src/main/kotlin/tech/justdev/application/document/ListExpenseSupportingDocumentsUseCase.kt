package tech.justdev.application.document

import jakarta.inject.Singleton
import tech.justdev.application.expense.ExpenseNotFoundException
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.domain.document.entity.ExpenseSupportingDocument
import tech.justdev.domain.expense.repository.ExpenseRepository
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId

data class ListExpenseSupportingDocumentsQuery(
    val group: GroupId,
    val expense: ExpenseId,
    val requestedBy: MemberEmail,
)

data class ListedExpenseSupportingDocument(
    val document: ExpenseSupportingDocument,
    val canDelete: Boolean,
)

data class ListExpenseSupportingDocumentsResult(
    val current: List<ListedExpenseSupportingDocument>,
    val history: List<ListedExpenseSupportingDocument>,
)

interface ListExpenseSupportingDocumentsUseCase {
    suspend operator fun invoke(query: ListExpenseSupportingDocumentsQuery): ListExpenseSupportingDocumentsResult
}

@Singleton
class ListExpenseSupportingDocumentsUseCaseImpl(
    private val groupAccessPolicy: GroupAccessPolicy,
    private val expenseRepository: ExpenseRepository,
) : ListExpenseSupportingDocumentsUseCase {
    override suspend operator fun invoke(query: ListExpenseSupportingDocumentsQuery): ListExpenseSupportingDocumentsResult {
        groupAccessPolicy.requireMember(query.group, query.requestedBy)

        val expense =
            expenseRepository.findByIdAndGroup(query.expense, query.group)
                ?: throw ExpenseNotFoundException(query.expense, query.group)
        val history = expense.supportingDocuments.all
        val currentIntentIds =
            expense.supportingDocuments.current
                .map(ExpenseSupportingDocument::sourceUploadIntent)
                .toSet()

        if (history.isEmpty()) {
            return ListExpenseSupportingDocumentsResult(current = emptyList(), history = emptyList())
        }

        val historyDocuments =
            history.map { document ->
                ListedExpenseSupportingDocument(
                    document = document,
                    canDelete =
                        expense.canChangeSupportingDocumentsBy(query.requestedBy) &&
                            document.sourceUploadIntent in currentIntentIds,
                )
            }

        return ListExpenseSupportingDocumentsResult(
            current = historyDocuments.filter { it.document.sourceUploadIntent in currentIntentIds },
            history = historyDocuments,
        )
    }
}
