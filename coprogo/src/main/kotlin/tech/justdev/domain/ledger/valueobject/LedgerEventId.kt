package tech.justdev.domain.ledger.valueobject

import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID

@JvmInline
value class LedgerEventId(
    private val value: UUID,
) {
    fun toPrimitive(): UUID = value

    companion object {
        fun fromName(value: String): LedgerEventId = LedgerEventId(UUID.nameUUIDFromBytes(value.toByteArray(UTF_8)))
    }
}
