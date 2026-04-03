package tech.justdev.domain.ledger.valueobject

import tech.justdev.domain.shared.money.MoneyAmount

/**
 * Signed balance projection expressed in integer cents.
 *
 * This type is reserved for net ledger balances, not for immutable financial event amounts.
 */
@JvmInline
value class NetBalanceAmount private constructor(private val amountInCents: Long) {
    operator fun plus(other: NetBalanceAmount): NetBalanceAmount = NetBalanceAmount(amountInCents + other.amountInCents)

    operator fun minus(other: NetBalanceAmount): NetBalanceAmount = NetBalanceAmount(amountInCents - other.amountInCents)

    operator fun unaryMinus(): NetBalanceAmount = NetBalanceAmount(-amountInCents)

    fun isZero(): Boolean = amountInCents == 0L

    fun inCents(): Long = amountInCents

    companion object {
        val ZERO = NetBalanceAmount(0)

        fun ofCents(amountInCents: Long): NetBalanceAmount = NetBalanceAmount(amountInCents)

        fun credit(amount: MoneyAmount): NetBalanceAmount = NetBalanceAmount(amount.inCents())

        fun debt(amount: MoneyAmount): NetBalanceAmount = NetBalanceAmount(-amount.inCents())
    }
}
