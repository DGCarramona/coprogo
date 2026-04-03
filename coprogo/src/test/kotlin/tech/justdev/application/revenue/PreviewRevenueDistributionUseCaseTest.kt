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
                    PreviewRevenueDistributionMember(memberId = memberUuid("alice"), percentage = BigDecimal("60.00")),
                    PreviewRevenueDistributionMember(memberId = memberUuid("bob"), percentage = BigDecimal("40.00")),
                ),
            ),
        )

        assertEquals(
            PreviewRevenueDistributionResult(
                totalAmountInCents = 100,
                allocations = listOf(
                    PreviewRevenueDistributionAllocation(memberId = memberUuid("alice"), amountInCents = 60),
                    PreviewRevenueDistributionAllocation(memberId = memberUuid("bob"), amountInCents = 40),
                ),
            ),
            result,
        )
    }
}
