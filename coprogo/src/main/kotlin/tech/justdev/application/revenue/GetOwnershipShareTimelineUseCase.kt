package tech.justdev.application.revenue

import jakarta.inject.Singleton
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.revenue.repository.OwnershipShareTimelineRepository
import tech.justdev.domain.shared.valueobject.GroupId
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class GetOwnershipShareTimelineQuery(
    val group: GroupId,
    val requestedBy: MemberEmail,
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

@Singleton
class GetOwnershipShareTimelineUseCase(
    private val groupAccessPolicy: GroupAccessPolicy,
    private val ownershipShareTimelineRepository: OwnershipShareTimelineRepository,
) {
    suspend operator fun invoke(query: GetOwnershipShareTimelineQuery): OwnershipShareTimelineSnapshot {
        groupAccessPolicy.requireMember(query.group, query.requestedBy)

        val timeline =
            ownershipShareTimelineRepository.findByGroup(query.group)
                ?: throw IllegalArgumentException("ownership share timeline for group ${query.group.toPrimitive()} was not found")

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
