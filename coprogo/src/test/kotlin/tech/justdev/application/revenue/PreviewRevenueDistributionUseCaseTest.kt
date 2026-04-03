package tech.justdev.application.revenue

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tech.justdev.testsupport.memberUuid
import java.math.BigDecimal

class PreviewRevenueDistributionUseCaseTest {

    private val useCase = PreviewRevenueDistributionUseCase()

    @Test
    fun `invoke should map members into ownership shares before distributing revenue`() {
        val result = useCase(
            PreviewRevenueDistributionCommand(
                amountInCents = 100,
                members = setOf(
                    PreviewRevenueDistributionMember(member = memberUuid("alice"), percentage = BigDecimal("60.00")),
                    PreviewRevenueDistributionMember(member = memberUuid("bob"), percentage = BigDecimal("40.00")),
                ),
            ),
        )

        assertEquals(
            PreviewRevenueDistributionResult(
                totalAmountInCents = 100,
                allocations = listOf(
                    PreviewRevenueDistributionAllocation(member = memberUuid("alice"), amountInCents = 60),
                    PreviewRevenueDistributionAllocation(member = memberUuid("bob"), amountInCents = 40),
                ),
            ),
            result,
        )
    }
}
