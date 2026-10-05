package tech.justdev.domain.reimbursement.entity

import tech.justdev.domain.document.entity.DocumentUploadIntent
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.reimbursement.valueobject.ReimbursementId
import tech.justdev.domain.reimbursement.valueobject.ReimbursementRejectionReason
import tech.justdev.domain.reimbursement.valueobject.ReimbursementStatus
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import java.time.Instant

class Reimbursement private constructor(
    val id: ReimbursementId,
    val group: GroupId,
    val paidBy: MemberEmail,
    val receivedBy: MemberEmail,
    val amount: MoneyAmount,
    val reimbursedAt: Instant,
    val declaredBy: MemberEmail,
    val declaredAt: Instant,
    val status: ReimbursementStatus,
    val supportingDocuments: List<ReimbursementSupportingDocument>,
) {
    fun accept(
        reviewedBy: MemberEmail,
        acceptedAt: Instant,
    ): Reimbursement {
        requireReviewableBy(reviewedBy, acceptedAt)

        return restoreWithStatus(ReimbursementStatus.Accepted(acceptedAt))
    }

    fun reject(
        reviewedBy: MemberEmail,
        rejectedAt: Instant,
        reason: ReimbursementRejectionReason? = null,
    ): Reimbursement {
        requireReviewableBy(reviewedBy, rejectedAt)

        return restoreWithStatus(ReimbursementStatus.Rejected(rejectedAt, reason))
    }

    private fun requireReviewableBy(
        reviewedBy: MemberEmail,
        decidedAt: Instant,
    ) {
        require(status == ReimbursementStatus.PendingReview) { "reimbursement is not awaiting review" }
        require(reviewedBy == receivedBy) { "only the reimbursement receiver can review it" }
        require(decidedAt >= declaredAt) { "reimbursement review decision must not precede its declaration" }
    }

    private fun restoreWithStatus(status: ReimbursementStatus): Reimbursement =
        restore(
            id = id,
            group = group,
            paidBy = paidBy,
            receivedBy = receivedBy,
            amount = amount,
            reimbursedAt = reimbursedAt,
            declaredBy = declaredBy,
            declaredAt = declaredAt,
            status = status,
            supportingDocuments = supportingDocuments,
        )

    companion object {
        fun recordDirect(
            id: ReimbursementId,
            group: GroupId,
            paidBy: MemberEmail,
            receivedBy: MemberEmail,
            amount: MoneyAmount,
            reimbursedAt: Instant,
            declaredBy: MemberEmail,
            declaredAt: Instant,
        ): Reimbursement {
            require(declaredBy == receivedBy) {
                "direct reimbursement must be recorded by its receiver"
            }

            return restore(
                id = id,
                group = group,
                paidBy = paidBy,
                receivedBy = receivedBy,
                amount = amount,
                reimbursedAt = reimbursedAt,
                declaredBy = declaredBy,
                declaredAt = declaredAt,
                status = ReimbursementStatus.Accepted(declaredAt),
            )
        }

        fun declareWithSupportingDocuments(
            id: ReimbursementId,
            group: GroupId,
            paidBy: MemberEmail,
            receivedBy: MemberEmail,
            amount: MoneyAmount,
            reimbursedAt: Instant,
            declaredBy: MemberEmail,
            declaredAt: Instant,
            supportingDocumentUploadIntents: List<DocumentUploadIntent>,
        ): Reimbursement {
            require(declaredBy == paidBy) {
                "documented reimbursement must be declared by its payer"
            }
            require(supportingDocumentUploadIntents.isNotEmpty()) {
                "documented reimbursement requires at least one supporting document"
            }
            val supportingDocuments = supportingDocumentUploadIntents.map(ReimbursementSupportingDocument::fromConsumedUploadIntent)

            require(supportingDocuments.map(ReimbursementSupportingDocument::group).toSet() == setOf(group)) {
                "reimbursement and supporting document must belong to the same group"
            }
            require(supportingDocuments.map(ReimbursementSupportingDocument::uploader).toSet() == setOf(declaredBy)) {
                "documented reimbursement supporting document must be uploaded by its declarer"
            }
            require(
                supportingDocuments.map(ReimbursementSupportingDocument::sourceUploadIntent).toSet().size == supportingDocuments.size,
            ) {
                "documented reimbursement supporting documents must use distinct upload intents"
            }

            return restore(
                id = id,
                group = group,
                paidBy = paidBy,
                receivedBy = receivedBy,
                amount = amount,
                reimbursedAt = reimbursedAt,
                declaredBy = declaredBy,
                declaredAt = declaredAt,
                status = ReimbursementStatus.PendingReview,
                supportingDocuments = supportingDocuments,
            )
        }

        fun restore(
            id: ReimbursementId,
            group: GroupId,
            paidBy: MemberEmail,
            receivedBy: MemberEmail,
            amount: MoneyAmount,
            reimbursedAt: Instant,
            declaredBy: MemberEmail,
            declaredAt: Instant,
            status: ReimbursementStatus,
            supportingDocuments: List<ReimbursementSupportingDocument> = emptyList(),
        ): Reimbursement {
            val restoredSupportingDocuments = supportingDocuments.toList()

            require(paidBy != receivedBy) {
                "reimbursement payer and receiver must be different"
            }
            require(declaredBy == paidBy || declaredBy == receivedBy) {
                "reimbursement must be declared by its payer or receiver"
            }
            require(amount > MoneyAmount.ZERO) {
                "reimbursement amount must be strictly positive"
            }
            require(reimbursedAt <= declaredAt) {
                "reimbursement must not occur after its declaration"
            }
            when (status) {
                ReimbursementStatus.PendingReview -> {
                    require(declaredBy == paidBy) {
                        "documented reimbursement must be declared by its payer"
                    }
                    require(restoredSupportingDocuments.isNotEmpty()) {
                        "documented reimbursement requires at least one supporting document"
                    }
                }

                is ReimbursementStatus.Accepted -> {
                    require(status.acceptedAt >= declaredAt) {
                        "reimbursement acceptance must not precede its declaration"
                    }
                }

                is ReimbursementStatus.Rejected -> {
                    require(status.decidedAt >= declaredAt) {
                        "reimbursement rejection must not precede its declaration"
                    }
                    require(restoredSupportingDocuments.isNotEmpty()) {
                        "rejected reimbursement must keep its supporting documents"
                    }
                }
            }
            if (restoredSupportingDocuments.isEmpty()) {
                require(declaredBy == receivedBy) {
                    "direct reimbursement must be recorded by its receiver"
                }
            } else {
                require(declaredBy == paidBy) {
                    "documented reimbursement must be declared by its payer"
                }
                require(restoredSupportingDocuments.map(ReimbursementSupportingDocument::group).toSet() == setOf(group)) {
                    "reimbursement and supporting document must belong to the same group"
                }
                require(restoredSupportingDocuments.map(ReimbursementSupportingDocument::uploader).toSet() == setOf(declaredBy)) {
                    "documented reimbursement supporting document must be uploaded by its declarer"
                }
                require(
                    restoredSupportingDocuments
                        .map(ReimbursementSupportingDocument::sourceUploadIntent)
                        .toSet()
                        .size == restoredSupportingDocuments.size,
                ) {
                    "documented reimbursement supporting documents must use distinct upload intents"
                }
            }

            return Reimbursement(
                id = id,
                group = group,
                paidBy = paidBy,
                receivedBy = receivedBy,
                amount = amount,
                reimbursedAt = reimbursedAt,
                declaredBy = declaredBy,
                declaredAt = declaredAt,
                status = status,
                supportingDocuments = restoredSupportingDocuments,
            )
        }
    }
}
