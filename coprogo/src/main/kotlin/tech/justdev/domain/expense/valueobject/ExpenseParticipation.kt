package tech.justdev.domain.expense.valueobject

import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.money.MoneyAmount
import java.time.Instant

sealed interface ExpenseParticipationStatus {
    data object Pending : ExpenseParticipationStatus

    data class Approved(
        val decidedAt: Instant,
    ) : ExpenseParticipationStatus

    data class Refused(
        val decidedAt: Instant,
    ) : ExpenseParticipationStatus
}

data class ExpenseParticipation(
    val member: MemberEmail,
    val amount: MoneyAmount,
    val status: ExpenseParticipationStatus,
) {
    init {
        require(amount > MoneyAmount.ZERO) { "participation amount must be > 0" }
    }
}
