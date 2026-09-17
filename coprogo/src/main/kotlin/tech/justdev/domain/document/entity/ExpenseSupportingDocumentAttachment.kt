package tech.justdev.domain.document.entity

import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.document.valueobject.SupportingDocumentAttachmentDeletion
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId
import java.time.Instant

class ExpenseSupportingDocumentAttachment private constructor(
    val sourceUploadIntent: DocumentUploadIntentId,
    val group: GroupId,
    val expense: ExpenseId,
    val replacesSourceUploadIntent: DocumentUploadIntentId?,
    val deletion: SupportingDocumentAttachmentDeletion?,
) {
    companion object {
        fun restore(
            sourceUploadIntent: DocumentUploadIntentId,
            group: GroupId,
            expense: ExpenseId,
            replacesSourceUploadIntent: DocumentUploadIntentId? = null,
            deletion: SupportingDocumentAttachmentDeletion? = null,
        ): ExpenseSupportingDocumentAttachment {
            require(sourceUploadIntent != replacesSourceUploadIntent) {
                "replaced and replacement documents must use distinct upload intents"
            }

            return ExpenseSupportingDocumentAttachment(
                sourceUploadIntent = sourceUploadIntent,
                group = group,
                expense = expense,
                replacesSourceUploadIntent = replacesSourceUploadIntent,
                deletion = deletion,
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
                deletion = null,
            )
        }
    }

    fun delete(
        by: MemberEmail,
        at: Instant,
    ): ExpenseSupportingDocumentAttachment {
        check(deletion == null) {
            "supporting document attachment has already been deleted"
        }

        return copy(deletion = SupportingDocumentAttachmentDeletion(by, at))
    }

    fun replaceWith(intent: DocumentUploadIntent): ExpenseSupportingDocumentAttachment {
        require(deletion == null) {
            "deleted supporting document cannot be replaced"
        }
        require(group == intent.group) {
            "replaced and replacement documents must belong to the same group"
        }
        require(sourceUploadIntent != intent.id) {
            "replaced and replacement documents must use distinct upload intents"
        }

        return attach(
            expense = expense,
            group = group,
            intent = intent,
        ).copyReplacing(sourceUploadIntent)
    }

    private fun copyReplacing(replaced: DocumentUploadIntentId): ExpenseSupportingDocumentAttachment =
        copy(replacesSourceUploadIntent = replaced)

    private fun copy(
        replacesSourceUploadIntent: DocumentUploadIntentId? = this.replacesSourceUploadIntent,
        deletion: SupportingDocumentAttachmentDeletion? = this.deletion,
    ): ExpenseSupportingDocumentAttachment =
        ExpenseSupportingDocumentAttachment(
            sourceUploadIntent = sourceUploadIntent,
            group = group,
            expense = expense,
            replacesSourceUploadIntent = replacesSourceUploadIntent,
            deletion = deletion,
        )
}
