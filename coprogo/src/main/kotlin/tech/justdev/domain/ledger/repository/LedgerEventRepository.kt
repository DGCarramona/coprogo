package tech.justdev.domain.ledger.repository

import tech.justdev.domain.ledger.event.LedgerEvent
import tech.justdev.domain.shared.valueobject.GroupId

interface LedgerEventRepository {
    suspend fun append(event: LedgerEvent)

    suspend fun findByGroup(group: GroupId): List<LedgerEvent>
}
