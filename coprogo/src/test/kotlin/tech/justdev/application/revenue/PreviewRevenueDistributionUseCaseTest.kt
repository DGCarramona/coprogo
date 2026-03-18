package tech.justdev.application.revenue

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tech.justdev.domain.revenue.RevenueDistributionService
import java.math.BigDecimal

class PreviewRevenueDistributionUseCaseTest {

    private val useCase = PreviewRevenueDistributionUseCase(RevenueDistributionService())

    @Test
    fun `invoke should map members into ownership shares before distributing revenue`() {
        val result = useCase(
            PreviewRevenueDistributionCommand(
                amountInCents = 100,
                members = setOf(
                    PreviewRevenueDistributionMember(memberId = "alice", percentage = BigDecimal("60.00")),
                    PreviewRevenueDistributionMember(memberId = "bob", percentage = BigDecimal("40.00")),
                ),
            ),
        )

        assertEquals(100, result.totalAmountInCents)
        assertEquals(mapOf("alice" to 60L, "bob" to 40L), result.allocationsInCents)
    }
}
