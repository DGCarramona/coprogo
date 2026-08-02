package tech.justdev.application.ledger

import jakarta.inject.Singleton
import tech.justdev.domain.ledger.valueobject.LedgerEventId
import java.util.UUID

fun interface LedgerEventIdGenerator {
    fun next(): LedgerEventId
}

@Singleton
class RandomLedgerEventIdGenerator : LedgerEventIdGenerator {
    override fun next(): LedgerEventId = LedgerEventId(UUID.randomUUID())
}
