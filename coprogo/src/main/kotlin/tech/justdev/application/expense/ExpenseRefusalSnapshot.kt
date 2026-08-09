package tech.justdev.application.expense

import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.valueobject.ExpenseParticipationStatus
import tech.justdev.domain.expense.valueobject.RefusalReason
import tech.justdev.domain.group.valueobject.MemberEmail
import java.time.Instant

data class ExpenseRefusalSnapshot(
    val member: MemberEmail,
    val refusedAt: Instant,
    val reason: RefusalReason?,
)

internal fun Expense.toRefusalSnapshot(): ExpenseRefusalSnapshot? =
    participations.firstNotNullOfOrNull { participation ->
        when (val status = participation.status) {
            ExpenseParticipationStatus.Pending -> null
            is ExpenseParticipationStatus.Approved -> null
            is ExpenseParticipationStatus.Refused ->
                ExpenseRefusalSnapshot(
                    member = participation.member,
                    refusedAt = status.decidedAt,
                    reason = status.reason,
                )
        }
    }
