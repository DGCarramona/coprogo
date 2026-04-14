package tech.justdev.application.revenue

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.memberEmailString
import java.math.BigDecimal

class PreviewRevenueDistributionUseCaseTest {
    private val useCase = PreviewRevenueDistributionUseCase()

    @Test
    fun `invoke should map members into ownership shares before distributing revenue`() {
        runTest {
            val result =
                useCase(
                    PreviewRevenueDistributionCommand(
                        amountInCents = 100,
                        members =
                            setOf(
                                PreviewRevenueDistributionMember(member = memberEmail("alice"), percentage = BigDecimal("60.00")),
                                PreviewRevenueDistributionMember(member = memberEmail("bob"), percentage = BigDecimal("40.00")),
                            ),
                    ),
                )

            assertEquals(
                PreviewRevenueDistributionResult(
                    totalAmountInCents = 100,
                    allocations =
                        listOf(
                            PreviewRevenueDistributionAllocation(member = memberEmailString("alice"), amountInCents = 60),
                            PreviewRevenueDistributionAllocation(member = memberEmailString("bob"), amountInCents = 40),
                        ),
                ),
                result,
            )
        }
    }
}
