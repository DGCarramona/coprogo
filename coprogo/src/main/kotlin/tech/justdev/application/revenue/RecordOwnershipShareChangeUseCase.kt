package tech.justdev.application.revenue

import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.revenue.entity.OwnershipShareChange
import tech.justdev.domain.revenue.entity.OwnershipShareTimeline
import tech.justdev.domain.revenue.repository.OwnershipShareTimelineRepository
import tech.justdev.domain.revenue.valueobject.OwnershipPercentage
import tech.justdev.domain.revenue.valueobject.OwnershipShare
import tech.justdev.domain.shared.valueobject.GroupId
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class RecordOwnershipShareChangeCommand(
    val group: UUID,
    val effectiveDate: LocalDate,
    val recordedBy: MemberEmail,
    val recordedAt: Instant,
    val shares: Set<RecordOwnershipShareCommand>,
)

data class RecordOwnershipShareCommand(
    val member: MemberEmail,
    val percentage: BigDecimal,
)

class RecordOwnershipShareChangeUseCase(
    private val ownershipShareTimelineRepository: OwnershipShareTimelineRepository,
    private val ownershipShareChangeIdGenerator: OwnershipShareChangeIdGenerator = RandomOwnershipShareChangeIdGenerator,
) {
    suspend operator fun invoke(command: RecordOwnershipShareChangeCommand) {
        val group = GroupId(command.group)
        val existingTimeline =
            ownershipShareTimelineRepository.findByGroup(group)
                ?: OwnershipShareTimeline.empty(group)

        val updatedTimeline =
            existingTimeline.recordChange(
                OwnershipShareChange(
                    id = ownershipShareChangeIdGenerator.next(),
                    effectiveDate = command.effectiveDate,
                    recordedBy = command.recordedBy,
                    recordedAt = command.recordedAt,
                    shares =
                        command.shares
                            .map { share ->
                                OwnershipShare(
                                    member = share.member,
                                    percentage = OwnershipPercentage.ofPercentage(share.percentage),
                                )
                            }.toSet(),
                ),
            )

        ownershipShareTimelineRepository.persist(updatedTimeline)
    }
}
