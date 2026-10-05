package tech.justdev.domain.ledger.valueobject

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tech.justdev.domain.shared.money.MoneyAmount

class NetBalanceAmountTest {
    @Nested
    inner class Arithmetic {
        @Test
        fun `should add subtract and negate signed balances`() {
            val credit = NetBalanceAmount.ofCents(75)
            val debt = NetBalanceAmount.ofCents(-20)

            assertEquals(
                listOf(55L, 95L, -75L),
                listOf((credit + debt).inCents(), (credit - debt).inCents(), (-credit).inCents()),
            )
        }
    }

    @Nested
    inner class Factories {
        @Test
        fun `should expose zero credit and debt with their expected signs`() {
            assertEquals(
                listOf(0L, 42L, -42L),
                listOf(
                    NetBalanceAmount.ZERO.inCents(),
                    NetBalanceAmount.credit(MoneyAmount.ofCents(42)).inCents(),
                    NetBalanceAmount.debt(MoneyAmount.ofCents(42)).inCents(),
                ),
            )
            assertTrue(NetBalanceAmount.ZERO.isZero())
            assertFalse(NetBalanceAmount.ofCents(1).isZero())
        }
    }
}
