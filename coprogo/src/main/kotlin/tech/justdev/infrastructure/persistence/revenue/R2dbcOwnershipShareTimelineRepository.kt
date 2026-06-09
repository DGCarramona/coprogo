package tech.justdev.infrastructure.persistence.revenue

import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import kotlinx.coroutines.flow.collect
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.revenue.entity.OwnershipShareChange
import tech.justdev.domain.revenue.entity.OwnershipShareChangeId
import tech.justdev.domain.revenue.entity.OwnershipShareTimeline
import tech.justdev.domain.revenue.repository.OwnershipShareTimelineRepository
import tech.justdev.domain.revenue.valueobject.OwnershipPercentage
import tech.justdev.domain.revenue.valueobject.OwnershipShare
import tech.justdev.domain.shared.valueobject.GroupId
import java.util.UUID

@Singleton
open class R2dbcOwnershipShareTimelineRepository(
    private val changeDataRepository: OwnershipShareChangeDataRepository,
    private val allocationDataRepository: OwnershipShareAllocationDataRepository,
) : OwnershipShareTimelineRepository {
    override suspend fun findByGroup(group: GroupId): OwnershipShareTimeline? {
        val changes = changeDataRepository.findByGroup(group.toPrimitive())
        if (changes.isEmpty()) {
            return null
        }

        val allocationsByChange =
            allocationDataRepository
                .findByGroup(group.toPrimitive())
                .groupBy { allocation -> allocation.changeId }

        return OwnershipShareTimeline(
            group = group,
            changes = changes.map { change -> change.toDomain(allocationsByChange.getValue(change.id)) },
        )
    }

    @Transactional
    override suspend fun persist(timeline: OwnershipShareTimeline) {
        val group = timeline.group.toPrimitive()
        val existingChanges = changeDataRepository.findByGroup(group).map { change -> change.id }.toSet()
        val existingAllocations =
            allocationDataRepository
                .findByGroup(group)
                .map { allocation -> allocation.changeId to allocation.memberEmail }
                .toSet()

        changeDataRepository
            .saveAll(
                timeline
                    .history()
                    .filterNot { change -> change.id.toPrimitive() in existingChanges }
                    .map { change -> change.toEntity(timeline.group) },
            ).collect()

        allocationDataRepository
            .saveAll(
                timeline
                    .history()
                    .flatMap { change ->
                        change.shares
                            .filterNot { share -> change.id.toPrimitive() to share.member.toPrimitive() in existingAllocations }
                            .map { share -> share.toEntity(change.id) }
                    },
            ).collect()
    }
}

private fun OwnershipShareChange.toEntity(group: GroupId): OwnershipShareChangeEntity =
    OwnershipShareChangeEntity(
        id = id.toPrimitive(),
        group = group.toPrimitive(),
        effectiveDate = effectiveDate,
        recordedBy = recordedBy.toPrimitive(),
        recordedAt = recordedAt,
    )

private fun OwnershipShare.toEntity(change: OwnershipShareChangeId): OwnershipShareAllocationEntity =
    OwnershipShareAllocationEntity(
        id = UUID.randomUUID(),
        changeId = change.toPrimitive(),
        memberEmail = member.toPrimitive(),
        basisPoints = percentage.inBasisPoints(),
    )

private fun OwnershipShareChangeEntity.toDomain(allocations: List<OwnershipShareAllocationEntity>): OwnershipShareChange =
    OwnershipShareChange(
        id = OwnershipShareChangeId(id),
        effectiveDate = effectiveDate,
        recordedBy = MemberEmail.of(recordedBy),
        recordedAt = recordedAt,
        shares = allocations.map(OwnershipShareAllocationEntity::toDomain).toSet(),
    )

private fun OwnershipShareAllocationEntity.toDomain(): OwnershipShare =
    OwnershipShare(
        member = MemberEmail.of(memberEmail),
        percentage = OwnershipPercentage.ofBasisPoints(basisPoints),
    )
