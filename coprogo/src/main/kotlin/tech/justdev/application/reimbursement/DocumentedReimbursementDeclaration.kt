package tech.justdev.application.reimbursement

import tech.justdev.domain.document.entity.DocumentUploadIntent
import tech.justdev.domain.document.entity.DocumentUploadIntentStatus
import tech.justdev.domain.reimbursement.entity.Reimbursement
import tech.justdev.domain.reimbursement.entity.ReimbursementSupportingDocument
import tech.justdev.domain.reimbursement.valueobject.ReimbursementStatus

class DocumentedReimbursementDeclaration private constructor(
    val reimbursement: Reimbursement,
    consumedUploadIntents: List<DocumentUploadIntent>,
) {
    val consumedUploadIntents: List<DocumentUploadIntent> = consumedUploadIntents.toList()

    companion object {
        fun from(
            reimbursement: Reimbursement,
            consumedUploadIntents: List<DocumentUploadIntent>,
        ): DocumentedReimbursementDeclaration {
            require(reimbursement.status == ReimbursementStatus.PendingReview) {
                "documented reimbursement declaration must be pending review"
            }
            require(
                consumedUploadIntents
                    .map { intent -> intent.status is DocumentUploadIntentStatus.Consumed }
                    .toSet() == setOf(true),
            ) {
                "documented reimbursement declaration requires consumed upload intents"
            }

            val documentsFromUploadIntents =
                consumedUploadIntents.map(ReimbursementSupportingDocument::fromConsumedUploadIntent)
            require(
                documentsFromUploadIntents.size == reimbursement.supportingDocuments.size &&
                    documentsFromUploadIntents.toSet() == reimbursement.supportingDocuments.toSet(),
            ) {
                "reimbursement supporting documents must exactly match consumed upload intents"
            }

            return DocumentedReimbursementDeclaration(reimbursement, consumedUploadIntents)
        }
    }
}
