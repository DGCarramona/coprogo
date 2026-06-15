package tech.justdev.application.ledger

import tech.justdev.domain.ledger.valueobject.LedgerEventId
import java.util.UUID

fun interface LedgerEventIdGenerator {
    fun next(): LedgerEventId
}

object RandomLedgerEventIdGenerator : LedgerEventIdGenerator {
    override fun next(): LedgerEventId = LedgerEventId(UUID.randomUUID())
}
