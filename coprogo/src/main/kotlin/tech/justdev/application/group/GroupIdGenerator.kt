package tech.justdev.application.group

import tech.justdev.domain.shared.valueobject.GroupId
import java.util.UUID

interface GroupIdGenerator {
    fun next(): GroupId
}

object RandomGroupIdGenerator : GroupIdGenerator {
    override fun next(): GroupId = GroupId(UUID.randomUUID())
}
