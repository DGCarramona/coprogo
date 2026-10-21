package tech.justdev.application.ledger

import tech.justdev.domain.ledger.event.CashPoolWithdrawalLedgerEvent
import tech.justdev.domain.ledger.event.LedgerEvent
import tech.justdev.domain.shared.valueobject.GroupId

interface CashPoolWithdrawalPersistence {
    suspend fun appendFromCurrentLedger(
        group: GroupId,
        build: suspend (List<LedgerEvent>) -> CashPoolWithdrawalLedgerEvent,
    )
}
