package tech.justdev.application.group

import tech.justdev.domain.group.entity.GroupInvitationId
import java.util.UUID

interface GroupInvitationIdGenerator {
    fun next(): GroupInvitationId
}

object RandomGroupInvitationIdGenerator : GroupInvitationIdGenerator {
    override fun next(): GroupInvitationId = GroupInvitationId(UUID.randomUUID())
}
