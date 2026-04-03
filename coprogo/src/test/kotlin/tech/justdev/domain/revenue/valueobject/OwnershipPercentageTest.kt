package tech.justdev.domain.revenue.valueobject

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class OwnershipPercentageTest {

    @Test
    fun `ofPercentage should convert a two-decimal percentage into basis points`() {
        assertEquals(3333, OwnershipPercentage.ofPercentage(BigDecimal("33.33")).inBasisPoints())
    }

    @Test
    fun `ofPercentage should reject values with more than two decimals`() {
        assertThrows(IllegalArgumentException::class.java) {
            OwnershipPercentage.ofPercentage(BigDecimal("33.333"))
        }
    }
}
