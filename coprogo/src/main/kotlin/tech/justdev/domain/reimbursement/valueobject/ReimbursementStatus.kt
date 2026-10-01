package tech.justdev.domain.reimbursement.valueobject

import java.time.Instant

sealed interface ReimbursementStatus {
    data object PendingReview : ReimbursementStatus

    data class Accepted(
        val acceptedAt: Instant,
    ) : ReimbursementStatus
}
