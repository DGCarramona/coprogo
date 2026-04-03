package tech.justdev.domain.expense.valueobject

import tech.justdev.domain.shared.valueobject.MemberId
import tech.justdev.domain.shared.money.MoneyAmount

data class ExpenseShare(
    val memberId: MemberId,
    val amount: MoneyAmount,
) {
    init {
        require(amount > MoneyAmount.ZERO) { "participation amount must be > 0" }
    }
}
