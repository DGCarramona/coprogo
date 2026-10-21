package tech.justdev.application.ledger

import tech.justdev.domain.shared.money.MoneyAmount

class InsufficientCashPoolBalanceException(
    val requestedAmount: MoneyAmount,
    val availableAmount: MoneyAmount,
) : IllegalArgumentException(
        "cash pool withdrawal amount ${requestedAmount.inCents()} exceeds available amount ${availableAmount.inCents()}",
    )
