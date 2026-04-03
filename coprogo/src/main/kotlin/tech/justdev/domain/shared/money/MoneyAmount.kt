package tech.justdev.domain.shared.money

/**
 * Money is stored and manipulated exclusively as integer cents.
 *
 * Decimal arithmetic should stay out of this type to avoid rounding drift on financial amounts.
 */
@JvmInline
value class MoneyAmount private constructor(private val amountInCents: Long) {
    init {
        require(amountInCents >= 0) { "amountInCents must be >= 0" }
    }

    operator fun plus(other: MoneyAmount): MoneyAmount = ofCents(amountInCents + other.amountInCents)

    operator fun minus(other: MoneyAmount): MoneyAmount {
        require(amountInCents >= other.amountInCents) { "resulting amount must be >= 0" }
        return ofCents(amountInCents - other.amountInCents)
    }

    operator fun compareTo(other: MoneyAmount): Int = amountInCents.compareTo(other.amountInCents)

    fun isZero(): Boolean = amountInCents == 0L

    fun inCents(): Long = amountInCents

    fun splitEvenly(parts: Int): List<MoneyAmount> {
        require(parts > 0) { "parts must be > 0" }

        val baseAmountInCents = amountInCents / parts
        val remainder = (amountInCents % parts).toInt()

        return (0 until parts).map { index ->
            val extraCent = if (index < remainder) 1L else 0L
            ofCents(baseAmountInCents + extraCent)
        }
    }

    companion object {
        val ZERO = MoneyAmount(0)

        fun ofCents(amountInCents: Long): MoneyAmount = MoneyAmount(amountInCents)
    }
}

fun Iterable<MoneyAmount>.sum(): MoneyAmount = fold(MoneyAmount.ZERO) { total, amount -> total + amount }
