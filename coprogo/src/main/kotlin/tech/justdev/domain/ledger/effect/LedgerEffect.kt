package tech.justdev.domain.ledger.effect

import tech.justdev.domain.ledger.valueobject.NetBalanceAmount
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.MemberId

sealed interface LedgerEffect

data class MemberBalanceTransfer(
    val fromMember: MemberId,
    val toMember: MemberId,
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
    val member: MemberId,
    val amount: NetBalanceAmount,
) : LedgerEffect {
    init {
        require(!amount.isZero()) { "member cash pool share delta must not be zero" }
    }

    companion object {
        fun increase(
            member: MemberId,
            amount: MoneyAmount,
        ): MemberCashPoolShareDelta = MemberCashPoolShareDelta(member = member, amount = NetBalanceAmount.credit(amount))

        fun decrease(
            member: MemberId,
            amount: MoneyAmount,
        ): MemberCashPoolShareDelta = MemberCashPoolShareDelta(member = member, amount = NetBalanceAmount.debt(amount))
    }
}
