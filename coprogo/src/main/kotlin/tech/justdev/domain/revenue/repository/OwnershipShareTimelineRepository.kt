package tech.justdev.domain.revenue.repository

import tech.justdev.domain.revenue.entity.OwnershipShareTimeline
import tech.justdev.domain.shared.valueobject.GroupId

interface OwnershipShareTimelineRepository {
    fun findByGroup(group: GroupId): OwnershipShareTimeline?

    fun save(timeline: OwnershipShareTimeline)
}
