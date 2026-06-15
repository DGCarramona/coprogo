package tech.justdev.interfaces.revenue

import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Status
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import tech.justdev.application.auth.AuthenticatedUserProvider
import tech.justdev.application.revenue.GetOwnershipShareTimelineQuery
import tech.justdev.application.revenue.GetOwnershipShareTimelineUseCase
import tech.justdev.application.revenue.OwnershipShareChangeSnapshot
import tech.justdev.application.revenue.OwnershipShareSnapshot
import tech.justdev.application.revenue.OwnershipShareTimelineSnapshot
import tech.justdev.application.revenue.PreviewRevenueDistributionAtDateQuery
import tech.justdev.application.revenue.PreviewRevenueDistributionAtDateUseCase
import tech.justdev.application.revenue.PreviewRevenueDistributionCommand
import tech.justdev.application.revenue.PreviewRevenueDistributionMember
import tech.justdev.application.revenue.PreviewRevenueDistributionUseCase
import tech.justdev.application.revenue.RecordOwnershipShareChangeCommand
import tech.justdev.application.revenue.RecordOwnershipShareChangeUseCase
import tech.justdev.application.revenue.RecordOwnershipShareCommand
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.interfaces.openapi.AuthenticatedApi
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Controller("/api")
@AuthenticatedApi
@Tag(name = "RevenueDistribution")
class RevenueDistributionController(
    private val authenticatedUserProvider: AuthenticatedUserProvider,
    private val previewRevenueDistributionUseCase: PreviewRevenueDistributionUseCase,
    private val recordOwnershipShareChangeUseCase: RecordOwnershipShareChangeUseCase,
    private val getOwnershipShareTimelineUseCase: GetOwnershipShareTimelineUseCase,
    private val previewRevenueDistributionAtDateUseCase: PreviewRevenueDistributionAtDateUseCase,
) {
    @Post("/revenue-distribution/preview")
    @Operation(summary = "Preview ownership-share-based revenue distribution")
    fun preview(
        @Valid @Body request: RevenueDistributionPreviewRequest,
    ): RevenueDistributionPreviewResponse {
        val distribution =
            previewRevenueDistributionUseCase(
                PreviewRevenueDistributionCommand(
                    amountInCents = request.amountInCents,
                    members =
                        request.members
                            .map { member ->
                                PreviewRevenueDistributionMember(
                                    member = MemberEmail.of(member.member),
                                    percentage = member.percentage,
                                )
                            }.toSet(),
                ),
            )

        return RevenueDistributionPreviewResponse(
            totalAmountInCents = distribution.totalAmountInCents,
            allocations =
                distribution.allocations
                    .map { allocation -> RevenueDistributionAllocation(allocation.member, allocation.amountInCents) },
        )
    }

    @Post("/groups/{groupId}/ownership-shares")
    @Status(HttpStatus.NO_CONTENT)
    @Operation(summary = "Record an ownership-share change for a group")
    suspend fun recordOwnershipShareChange(
        @PathVariable groupId: UUID,
        @Valid @Body request: RecordOwnershipShareChangeRequest,
    ) {
        val authenticatedUser = authenticatedUserProvider.currentAuthenticatedUser()
        recordOwnershipShareChangeUseCase(
            RecordOwnershipShareChangeCommand(
                group = GroupId(groupId),
                effectiveDate = request.effectiveDate,
                recordedBy = authenticatedUser.email,
                recordedAt = Instant.now(),
                shares =
                    request.shares
                        .map { share ->
                            RecordOwnershipShareCommand(
                                member = MemberEmail.of(share.member),
                                percentage = share.percentage,
                            )
                        }.toSet(),
            ),
        )
    }

    @Get("/groups/{groupId}/ownership-shares")
    @Operation(summary = "Get ownership-share history for a group")
    suspend fun getOwnershipShareTimeline(
        @PathVariable groupId: UUID,
    ): OwnershipShareTimelineResponse {
        val authenticatedUser = authenticatedUserProvider.currentAuthenticatedUser()

        return getOwnershipShareTimelineUseCase(
            GetOwnershipShareTimelineQuery(
                group = GroupId(groupId),
                requestedBy = authenticatedUser.email,
            ),
        ).toResponse()
    }

    @Post("/groups/{groupId}/revenue-distribution/preview")
    @Operation(summary = "Preview revenue distribution from the recorded ownership-share timeline")
    suspend fun previewAtDate(
        @PathVariable groupId: UUID,
        @Valid @Body request: RevenueDistributionAtDatePreviewRequest,
    ): RevenueDistributionAtDatePreviewResponse {
        val authenticatedUser = authenticatedUserProvider.currentAuthenticatedUser()

        val preview =
            previewRevenueDistributionAtDateUseCase(
                PreviewRevenueDistributionAtDateQuery(
                    group = GroupId(groupId),
                    requestedBy = authenticatedUser.email,
                    amount = MoneyAmount.ofCents(request.amountInCents),
                    effectiveDate = request.effectiveDate,
                ),
            )

        return RevenueDistributionAtDatePreviewResponse(
            group = preview.group,
            effectiveDate = preview.effectiveDate,
            totalAmountInCents = preview.totalAmountInCents,
            allocations =
                preview.allocations.map { allocation ->
                    RevenueDistributionAllocation(
                        memberId = allocation.member,
                        amountInCents = allocation.amountInCents,
                    )
                },
        )
    }
}

@Serdeable
data class RevenueDistributionPreviewRequest(
    @field:Min(0)
    val amountInCents: Long,
    @field:NotEmpty
    val members: Set<RevenueDistributionMemberInput>,
)

@Serdeable
data class RevenueDistributionMemberInput(
    val member: String,
    @field:Positive
    val percentage: BigDecimal,
)

@Serdeable
data class RevenueDistributionPreviewResponse(
    val totalAmountInCents: Long,
    val allocations: List<RevenueDistributionAllocation>,
)

@Serdeable
data class RevenueDistributionAllocation(
    val memberId: String,
    val amountInCents: Long,
)

@Serdeable
data class RecordOwnershipShareChangeRequest(
    val effectiveDate: LocalDate,
    @field:NotEmpty
    val shares: Set<OwnershipShareInput>,
)

@Serdeable
data class OwnershipShareInput(
    val member: String,
    @field:Positive
    val percentage: BigDecimal,
)

@Serdeable
data class OwnershipShareTimelineResponse(
    val group: UUID,
    val changes: List<OwnershipShareChangeResponse>,
)

@Serdeable
data class OwnershipShareChangeResponse(
    val change: UUID,
    val effectiveDate: LocalDate,
    val recordedBy: String,
    val recordedAt: Instant,
    val shares: List<OwnershipShareResponse>,
)

@Serdeable
data class OwnershipShareResponse(
    val member: String,
    val percentage: BigDecimal,
)

@Serdeable
data class RevenueDistributionAtDatePreviewRequest(
    @field:Min(0)
    val amountInCents: Long,
    val effectiveDate: LocalDate,
)

@Serdeable
data class RevenueDistributionAtDatePreviewResponse(
    val group: UUID,
    val effectiveDate: LocalDate,
    val totalAmountInCents: Long,
    val allocations: List<RevenueDistributionAllocation>,
)

private fun OwnershipShareTimelineSnapshot.toResponse(): OwnershipShareTimelineResponse =
    OwnershipShareTimelineResponse(
        group = group,
        changes = changes.map(OwnershipShareChangeSnapshot::toResponse),
    )

private fun OwnershipShareChangeSnapshot.toResponse(): OwnershipShareChangeResponse =
    OwnershipShareChangeResponse(
        change = change,
        effectiveDate = effectiveDate,
        recordedBy = recordedBy,
        recordedAt = recordedAt,
        shares = shares.map(OwnershipShareSnapshot::toResponse),
    )

private fun OwnershipShareSnapshot.toResponse(): OwnershipShareResponse =
    OwnershipShareResponse(
        member = member,
        percentage = percentage,
    )
