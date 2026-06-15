package tech.justdev.infrastructure.persistence.ledger

import io.micronaut.core.annotation.Introspected
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import java.time.Instant
import java.util.UUID

@MappedEntity("ledger_events")
data class LedgerEventEntity(
    @field:Id
    val id: UUID,
    @field:MappedProperty("group")
    val group: UUID,
    @field:MappedProperty("type")
    val eventType: String,
    @field:MappedProperty("occurred_at")
    val occurredAt: Instant,
)

@MappedEntity("ledger_accepted_expense_events")
data class LedgerAcceptedExpenseEventEntity(
    @field:Id
    @field:MappedProperty("event")
    val eventId: UUID,
    val expense: UUID,
    @field:MappedProperty("paid_by")
    val paidBy: String,
)

@MappedEntity("ledger_cash_pool_income_events")
data class LedgerCashPoolIncomeEventEntity(
    @field:Id
    @field:MappedProperty("event")
    val eventId: UUID,
    @field:MappedProperty("amount_in_cents")
    val amountInCents: Long,
)

@MappedEntity("ledger_cash_pool_withdrawal_events")
data class LedgerCashPoolWithdrawalEventEntity(
    @field:Id
    @field:MappedProperty("event")
    val eventId: UUID,
    @field:MappedProperty("withdrawn_by")
    val withdrawnBy: String,
    @field:MappedProperty("withdrawn_amount_in_cents")
    val withdrawnAmountInCents: Long,
    @field:MappedProperty("own_revenue_share_consumed_in_cents")
    val ownRevenueShareConsumedInCents: Long,
)

@Introspected
data class LedgerEventRow(
    val id: UUID,
    @field:MappedProperty("group")
    val group: UUID,
    @field:MappedProperty("event_type")
    val eventType: String,
    @field:MappedProperty("occurred_at")
    val occurredAt: Instant,
    val expense: UUID?,
    @field:MappedProperty("paid_by")
    val paidBy: String?,
    @field:MappedProperty("income_amount_in_cents")
    val incomeAmountInCents: Long?,
    @field:MappedProperty("withdrawn_by")
    val withdrawnBy: String?,
    @field:MappedProperty("withdrawn_amount_in_cents")
    val withdrawnAmountInCents: Long?,
    @field:MappedProperty("own_revenue_share_consumed_in_cents")
    val ownRevenueShareConsumedInCents: Long?,
)

@MappedEntity("ledger_member_balance_transfers")
data class LedgerMemberBalanceTransferEntity(
    @field:Id
    val id: UUID,
    @field:MappedProperty("event")
    val eventId: UUID,
    @field:MappedProperty("from_member")
    val fromMember: String,
    @field:MappedProperty("to_member")
    val toMember: String,
    @field:MappedProperty("amount_in_cents")
    val amountInCents: Long,
)

@MappedEntity("ledger_member_cash_pool_share_deltas")
data class LedgerMemberCashPoolShareDeltaEntity(
    @field:Id
    val id: UUID,
    @field:MappedProperty("event")
    val eventId: UUID,
    @field:MappedProperty("member_email")
    val memberEmail: String,
    @field:MappedProperty("amount_in_cents")
    val amountInCents: Long,
)
