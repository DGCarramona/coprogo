package tech.justdev.domain.ledger.effect

import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.ledger.valueobject.NetBalanceAmount
import tech.justdev.domain.shared.money.MoneyAmount

sealed interface LedgerEffect

data class MemberBalanceTransfer(
    val fromMember: MemberEmail,
    val toMember: MemberEmail,
    val amount: MoneyAmount,
) : LedgerEffect {
    init {
        require(fromMember != toMember) { "transfer participants must be different" }
        require(amount > MoneyAmount.ZERO) { "transfer amount must be > 0" }
    }
}

data class CashPoolBalanceDelta(
    val amount: NetBalanceAmount,
) : LedgerEffect {
    init {
        require(!amount.isZero()) { "cash pool balance delta must not be zero" }
    }

    companion object {
        fun increase(amount: MoneyAmount): CashPoolBalanceDelta = CashPoolBalanceDelta(NetBalanceAmount.credit(amount))

        fun decrease(amount: MoneyAmount): CashPoolBalanceDelta = CashPoolBalanceDelta(NetBalanceAmount.debt(amount))
    }
}

data class MemberCashPoolShareDelta(
    val member: MemberEmail,
    val amount: NetBalanceAmount,
) : LedgerEffect {
    init {
        require(!amount.isZero()) { "member cash pool share delta must not be zero" }
    }

    companion object {
        fun increase(
            member: MemberEmail,
            amount: MoneyAmount,
        ): MemberCashPoolShareDelta = MemberCashPoolShareDelta(member = member, amount = NetBalanceAmount.credit(amount))

        fun decrease(
            member: MemberEmail,
            amount: MoneyAmount,
        ): MemberCashPoolShareDelta = MemberCashPoolShareDelta(member = member, amount = NetBalanceAmount.debt(amount))
    }
}
