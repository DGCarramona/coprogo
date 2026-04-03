package tech.justdev.domain.ledger.valueobject

@JvmInline
value class LedgerEventId(val value: String) {
    init {
        require(value.isNotBlank()) { "ledgerEventId must not be blank" }
    }
}
