package tech.justdev.domain.revenue

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class RevenueDistributionServiceTest {

    private val service = RevenueDistributionService()

    @Test
    fun `distribute should split cents with deterministic remainder allocation`() {
        val result = service.distribute(
            totalAmountInCents = 100,
            ownershipShares = setOf(
                OwnershipShare(memberId = "alice", percentage = BigDecimal("33.33")),
                OwnershipShare(memberId = "bob", percentage = BigDecimal("33.33")),
                OwnershipShare(memberId = "carol", percentage = BigDecimal("33.34")),
            ),
        )

        assertEquals(100, result.totalAmountInCents)
        assertEquals(100, result.allocationsInCents.values.sum())
        assertEquals(34, result.allocationsInCents.getValue("carol"))
    }

    @Test
    fun `distribute should fail when shares do not sum to one hundred`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.distribute(
                totalAmountInCents = 100,
                ownershipShares = setOf(
                    OwnershipShare(memberId = "alice", percentage = BigDecimal("40.00")),
                    OwnershipShare(memberId = "bob", percentage = BigDecimal("50.00")),
                ),
            )
        }
    }
}
