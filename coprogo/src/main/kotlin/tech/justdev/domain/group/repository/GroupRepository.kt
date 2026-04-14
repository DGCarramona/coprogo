package tech.justdev.domain.group.repository

import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.shared.valueobject.GroupId

interface GroupRepository {
    suspend fun findById(id: GroupId): Group?

    suspend fun persist(group: Group)
}
