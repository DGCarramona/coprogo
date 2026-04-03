package tech.justdev.application.revenue

import tech.justdev.domain.revenue.entity.OwnershipShareChangeId
import java.util.UUID

fun interface OwnershipShareChangeIdGenerator {
    fun next(): OwnershipShareChangeId
}

object RandomOwnershipShareChangeIdGenerator : OwnershipShareChangeIdGenerator {
    override fun next(): OwnershipShareChangeId = OwnershipShareChangeId(UUID.randomUUID())
}
