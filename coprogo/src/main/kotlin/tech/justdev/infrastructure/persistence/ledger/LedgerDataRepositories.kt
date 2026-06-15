package tech.justdev.infrastructure.persistence.ledger

import io.micronaut.data.annotation.Query
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.time.Instant
import java.util.UUID

@R2dbcRepository(dialect = Dialect.POSTGRES)
interface LedgerEventDataRepository : CoroutineCrudRepository<LedgerEventEntity, UUID> {
    @Query(
        value =
            """
            INSERT INTO ledger_events (
                id,
                "group",
                type,
                occurred_at
            ) VALUES (
                :id,
                :group,
                CAST(:eventType AS ledger_event_type),
                :occurredAt
            )
            """,
        nativeQuery = true,
    )
    suspend fun persist(
        id: UUID,
        group: UUID,
        eventType: String,
        occurredAt: Instant,
    )

    @Query(
        value =
            """
            SELECT *
            FROM ledger_events
            WHERE "group" = :group
            ORDER BY occurred_at, id
            """,
        nativeQuery = true,
    )
    suspend fun findByGroup(group: UUID): List<LedgerEventEntity>

    @Query(
        value =
            """
            SELECT
                event.id,
                event."group",
                event.type AS event_type,
                event.occurred_at,
                accepted.expense,
                accepted.paid_by,
                income.amount_in_cents AS income_amount_in_cents,
                distribution.total_amount_in_cents AS distribution_total_amount_in_cents,
                withdrawal.withdrawn_by,
                withdrawal.withdrawn_amount_in_cents,
                withdrawal.own_revenue_share_consumed_in_cents
            FROM ledger_events event
            LEFT JOIN ledger_accepted_expense_events accepted ON accepted.event = event.id
            LEFT JOIN ledger_cash_pool_income_events income ON income.event = event.id
            LEFT JOIN ledger_revenue_distribution_events distribution ON distribution.event = event.id
            LEFT JOIN ledger_cash_pool_withdrawal_events withdrawal ON withdrawal.event = event.id
            WHERE event."group" = :group
            ORDER BY event.occurred_at, event.id
            """,
        nativeQuery = true,
    )
    suspend fun findRowsByGroup(group: UUID): List<LedgerEventRow>
}

@R2dbcRepository(dialect = Dialect.POSTGRES)
interface LedgerAcceptedExpenseEventDataRepository : CoroutineCrudRepository<LedgerAcceptedExpenseEventEntity, UUID>

@R2dbcRepository(dialect = Dialect.POSTGRES)
interface LedgerCashPoolIncomeEventDataRepository : CoroutineCrudRepository<LedgerCashPoolIncomeEventEntity, UUID>

@R2dbcRepository(dialect = Dialect.POSTGRES)
interface LedgerRevenueDistributionEventDataRepository : CoroutineCrudRepository<LedgerRevenueDistributionEventEntity, UUID>

@R2dbcRepository(dialect = Dialect.POSTGRES)
interface LedgerCashPoolWithdrawalEventDataRepository : CoroutineCrudRepository<LedgerCashPoolWithdrawalEventEntity, UUID>

@R2dbcRepository(dialect = Dialect.POSTGRES)
interface LedgerMemberBalanceTransferDataRepository : CoroutineCrudRepository<LedgerMemberBalanceTransferEntity, UUID> {
    @Query(
        value =
            """
            SELECT transfer.*
            FROM ledger_member_balance_transfers transfer
            JOIN ledger_events ledger_event ON ledger_event.id = transfer.event
            WHERE ledger_event."group" = :group
            ORDER BY ledger_event.occurred_at, transfer.from_member, transfer.to_member, transfer.amount_in_cents
            """,
        nativeQuery = true,
    )
    suspend fun findByGroup(group: UUID): List<LedgerMemberBalanceTransferEntity>
}

@R2dbcRepository(dialect = Dialect.POSTGRES)
interface LedgerMemberCashPoolShareDeltaDataRepository : CoroutineCrudRepository<LedgerMemberCashPoolShareDeltaEntity, UUID> {
    @Query(
        value =
            """
            SELECT delta.*
            FROM ledger_member_cash_pool_share_deltas delta
            JOIN ledger_events ledger_event ON ledger_event.id = delta.event
            WHERE ledger_event."group" = :group
            ORDER BY ledger_event.occurred_at, delta.member_email, delta.amount_in_cents
            """,
        nativeQuery = true,
    )
    suspend fun findByGroup(group: UUID): List<LedgerMemberCashPoolShareDeltaEntity>
}
