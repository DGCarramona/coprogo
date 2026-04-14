package tech.justdev.application.revenue

import tech.justdev.domain.revenue.repository.OwnershipShareTimelineRepository
import tech.justdev.domain.shared.valueobject.GroupId
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class GetOwnershipShareTimelineQuery(
    val group: UUID,
)

data class OwnershipShareTimelineSnapshot(
    val group: UUID,
    val changes: List<OwnershipShareChangeSnapshot>,
)

data class OwnershipShareChangeSnapshot(
    val change: UUID,
    val effectiveDate: LocalDate,
    val recordedBy: String,
    val recordedAt: Instant,
    val shares: List<OwnershipShareSnapshot>,
)

data class OwnershipShareSnapshot(
    val member: String,
    val percentage: BigDecimal,
)

class GetOwnershipShareTimelineUseCase(
    private val ownershipShareTimelineRepository: OwnershipShareTimelineRepository,
) {
    suspend operator fun invoke(query: GetOwnershipShareTimelineQuery): OwnershipShareTimelineSnapshot {
        val timeline =
            ownershipShareTimelineRepository.findByGroup(GroupId(query.group))
                ?: throw IllegalArgumentException("ownership share timeline for group ${query.group} was not found")

        return OwnershipShareTimelineSnapshot(
            group = timeline.group.toPrimitive(),
            changes =
                timeline.history().map { change ->
                    OwnershipShareChangeSnapshot(
                        change = change.id.toPrimitive(),
                        effectiveDate = change.effectiveDate,
                        recordedBy = change.recordedBy.toPrimitive(),
                        recordedAt = change.recordedAt,
                        shares =
                            change.shares
                                .sortedBy { share -> share.member.toPrimitive() }
                                .map { share ->
                                    OwnershipShareSnapshot(
                                        member = share.member.toPrimitive(),
                                        percentage = BigDecimal.valueOf(share.percentage.inBasisPoints().toLong(), 2),
                                    )
                                },
                    )
                },
        )
    }
}
