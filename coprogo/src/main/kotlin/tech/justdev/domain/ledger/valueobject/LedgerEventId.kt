package tech.justdev.domain.ledger.valueobject

import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID

@JvmInline
value class LedgerEventId(val value: UUID) {
    override fun toString(): String {
        return value.toString()
    }

    companion object {
        fun fromName(value: String): LedgerEventId {
            return LedgerEventId(UUID.nameUUIDFromBytes(value.toByteArray(UTF_8)))
        }
    }
}
