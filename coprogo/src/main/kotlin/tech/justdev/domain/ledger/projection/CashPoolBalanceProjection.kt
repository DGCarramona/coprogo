package tech.justdev.domain.ledger.projection

import tech.justdev.domain.ledger.effect.CashPoolBalanceDelta
import tech.justdev.domain.ledger.event.LedgerEvent
import tech.justdev.domain.ledger.valueobject.NetBalanceAmount

fun Iterable<LedgerEvent>.projectCashPoolBalance(): NetBalanceAmount =
    fold(NetBalanceAmount.ZERO) { balance, event ->
        event.effects
            .filterIsInstance<CashPoolBalanceDelta>()
            .fold(balance) { currentBalance, delta -> currentBalance + delta.amount }
    }
