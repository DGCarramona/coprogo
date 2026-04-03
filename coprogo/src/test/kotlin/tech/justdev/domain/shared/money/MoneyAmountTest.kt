package tech.justdev.domain.shared.money

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MoneyAmountTest {

    @Test
    fun `splitEvenly should distribute remainder using integer cent arithmetic only`() {
        assertEquals(
            listOf(
                MoneyAmount.ofCents(34),
                MoneyAmount.ofCents(33),
                MoneyAmount.ofCents(33),
            ),
            MoneyAmount.ofCents(100).splitEvenly(3),
        )
    }

    @Test
    fun `minus should keep amounts non negative`() {
        assertEquals(MoneyAmount.ofCents(40), MoneyAmount.ofCents(100) - MoneyAmount.ofCents(60))
    }

    @Test
    fun `inCents should expose primitive cents only at the boundary`() {
        assertEquals(123L, MoneyAmount.ofCents(123).inCents())
    }

    @Test
    fun `minus should fail when result would be negative`() {
        assertThrows(IllegalArgumentException::class.java) {
            MoneyAmount.ofCents(10) - MoneyAmount.ofCents(11)
        }
    }
}
