package tech.justdev.domain.document.entity

import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.shared.valueobject.GroupId

class ExpenseSupportingDocumentAttachment private constructor(
    val sourceUploadIntent: DocumentUploadIntentId,
    val group: GroupId,
    val expense: ExpenseId,
) {
    companion object {
        fun attach(
            expense: ExpenseId,
            group: GroupId,
            intent: DocumentUploadIntent,
        ): ExpenseSupportingDocumentAttachment {
            when (intent.status) {
                DocumentUploadIntentStatus.Pending,
                is DocumentUploadIntentStatus.Ready,
                -> throw IllegalArgumentException("expense supporting document requires a consumed upload intent")

                is DocumentUploadIntentStatus.Consumed -> Unit
            }
            require(intent.group == group) {
                "expense and supporting document must belong to the same group"
            }

            return ExpenseSupportingDocumentAttachment(
                sourceUploadIntent = intent.id,
                group = group,
                expense = expense,
            )
        }
    }
}
