package tech.justdev.interfaces.revenue

import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import tech.justdev.application.revenue.PreviewRevenueDistributionCommand
import tech.justdev.application.revenue.PreviewRevenueDistributionMember
import tech.justdev.application.revenue.PreviewRevenueDistributionUseCase
import java.math.BigDecimal

@Controller("/api/revenue-distribution")
@Tag(name = "RevenueDistribution")
class RevenueDistributionController(
    private val previewRevenueDistributionUseCase: PreviewRevenueDistributionUseCase,
) {

    @Post("/preview")
    @Operation(summary = "Preview ownership-share-based revenue distribution")
    fun preview(@Valid @Body request: RevenueDistributionPreviewRequest): RevenueDistributionPreviewResponse {
        val distribution = previewRevenueDistributionUseCase(
            PreviewRevenueDistributionCommand(
                amountInCents = request.amountInCents,
                members = request.members.map { member ->
                    PreviewRevenueDistributionMember(
                        memberId = member.memberId,
                        percentage = member.percentage,
                    )
                }.toSet(),
            ),
        )

        return RevenueDistributionPreviewResponse(
            totalAmountInCents = distribution.totalAmountInCents,
            allocations = distribution.allocationsInCents
                .map { (memberId, allocated) -> RevenueDistributionAllocation(memberId, allocated) }
                .sortedBy { it.memberId },
        )
    }
}

data class RevenueDistributionPreviewRequest(
    @field:Min(0)
    val amountInCents: Long,
    @field:NotEmpty
    val members: Set<RevenueDistributionMemberInput>,
)

data class RevenueDistributionMemberInput(
    @field:NotBlank
    val memberId: String,
    @field:Positive
    val percentage: BigDecimal,
)

data class RevenueDistributionPreviewResponse(
    val totalAmountInCents: Long,
    val allocations: List<RevenueDistributionAllocation>,
)

data class RevenueDistributionAllocation(
    val memberId: String,
    val amountInCents: Long,
)
