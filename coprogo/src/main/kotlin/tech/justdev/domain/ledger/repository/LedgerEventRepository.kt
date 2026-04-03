package tech.justdev.domain.ledger.repository

import tech.justdev.domain.ledger.event.LedgerEvent
import tech.justdev.domain.shared.valueobject.GroupId

interface LedgerEventRepository {
    fun append(event: LedgerEvent)

    fun findByGroup(group: GroupId): List<LedgerEvent>
}
