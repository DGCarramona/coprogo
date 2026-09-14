package tech.justdev.domain.document.entity

import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.shared.valueobject.GroupId

class ExpenseSupportingDocumentAttachment private constructor(
    val sourceUploadIntent: DocumentUploadIntentId,
    val group: GroupId,
    val expense: ExpenseId,
    val replacesSourceUploadIntent: DocumentUploadIntentId?,
) {
    companion object {
        fun restore(
            sourceUploadIntent: DocumentUploadIntentId,
            group: GroupId,
            expense: ExpenseId,
            replacesSourceUploadIntent: DocumentUploadIntentId? = null,
        ): ExpenseSupportingDocumentAttachment {
            require(sourceUploadIntent != replacesSourceUploadIntent) {
                "replaced and replacement documents must use distinct upload intents"
            }

            return ExpenseSupportingDocumentAttachment(
                sourceUploadIntent = sourceUploadIntent,
                group = group,
                expense = expense,
                replacesSourceUploadIntent = replacesSourceUploadIntent,
            )
        }

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
                replacesSourceUploadIntent = null,
            )
        }

        fun replace(
            replaced: ExpenseSupportingDocumentAttachment,
            intent: DocumentUploadIntent,
        ): ExpenseSupportingDocumentAttachment {
            require(replaced.group == intent.group) {
                "replaced and replacement documents must belong to the same group"
            }
            require(replaced.sourceUploadIntent != intent.id) {
                "replaced and replacement documents must use distinct upload intents"
            }

            return attach(
                expense = replaced.expense,
                group = replaced.group,
                intent = intent,
            ).copyReplacing(replaced.sourceUploadIntent)
        }
    }

    private fun copyReplacing(replaced: DocumentUploadIntentId): ExpenseSupportingDocumentAttachment =
        ExpenseSupportingDocumentAttachment(
            sourceUploadIntent = sourceUploadIntent,
            group = group,
            expense = expense,
            replacesSourceUploadIntent = replaced,
        )
}
