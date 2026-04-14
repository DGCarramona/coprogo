package tech.justdev.domain.expense.valueobject

import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.money.MoneyAmount

data class ExpenseShare(
    val member: MemberEmail,
    val amount: MoneyAmount,
) {
    init {
        require(amount > MoneyAmount.ZERO) { "participation amount must be > 0" }
    }
}
