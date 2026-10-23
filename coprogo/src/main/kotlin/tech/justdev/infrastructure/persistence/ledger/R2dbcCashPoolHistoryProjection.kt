package tech.justdev.infrastructure.persistence.ledger

import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.jooq.DSLContext
import tech.justdev.application.ledger.CashPoolHistoryProjection
import tech.justdev.application.ledger.CashPoolIncomeAllocationSnapshot
import tech.justdev.application.ledger.CashPoolIncomeSnapshot
import tech.justdev.application.ledger.CashPoolOperationSnapshot
import tech.justdev.application.ledger.CashPoolWithdrawalSnapshot
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.infrastructure.persistence.jooq.Tables.LEDGER_CASH_POOL_INCOME_EVENTS
import tech.justdev.infrastructure.persistence.jooq.Tables.LEDGER_CASH_POOL_WITHDRAWAL_EVENTS
import tech.justdev.infrastructure.persistence.jooq.Tables.LEDGER_EVENTS
import tech.justdev.infrastructure.persistence.jooq.Tables.LEDGER_MEMBER_CASH_POOL_SHARE_DELTAS
import tech.justdev.infrastructure.persistence.jooq.awaitList
import tech.justdev.infrastructure.persistence.jooq.dsl
import tech.justdev.infrastructure.persistence.jooq.enums.LedgerEventType
import java.time.Instant
import java.util.UUID

@Singleton
class R2dbcCashPoolHistoryProjection(
    @param:Named("default")
    private val connectionFactory: ConnectionFactory,
) : CashPoolHistoryProjection {
    override suspend fun findByGroup(group: GroupId): List<CashPoolOperationSnapshot> {
        val dsl = connectionFactory.dsl()
        val allocationsByIncome = dsl.findIncomeAllocations(group.toPrimitive()).groupBy { allocation -> allocation.event }

        return dsl
            .findCashPoolOperations(group.toPrimitive())
            .map { operation -> operation.toSnapshot(allocationsByIncome[operation.id].orEmpty()) }
    }
}

private suspend fun DSLContext.findCashPoolOperations(group: UUID): List<CashPoolOperationRow> =
    select(
        LEDGER_EVENTS.ID,
        LEDGER_EVENTS.TYPE,
        LEDGER_EVENTS.OCCURRED_AT,
        LEDGER_CASH_POOL_INCOME_EVENTS.AMOUNT_IN_CENTS,
        LEDGER_CASH_POOL_WITHDRAWAL_EVENTS.WITHDRAWN_BY,
        LEDGER_CASH_POOL_WITHDRAWAL_EVENTS.WITHDRAWN_AMOUNT_IN_CENTS,
        LEDGER_CASH_POOL_WITHDRAWAL_EVENTS.OWN_REVENUE_SHARE_CONSUMED_IN_CENTS,
    ).from(LEDGER_EVENTS)
        .leftJoin(LEDGER_CASH_POOL_INCOME_EVENTS)
        .on(LEDGER_CASH_POOL_INCOME_EVENTS.EVENT.eq(LEDGER_EVENTS.ID))
        .leftJoin(LEDGER_CASH_POOL_WITHDRAWAL_EVENTS)
        .on(LEDGER_CASH_POOL_WITHDRAWAL_EVENTS.EVENT.eq(LEDGER_EVENTS.ID))
        .where(LEDGER_EVENTS.GROUP.eq(group))
        .and(LEDGER_EVENTS.TYPE.`in`(LedgerEventType.CASH_POOL_INCOME, LedgerEventType.CASH_POOL_WITHDRAWAL))
        .orderBy(LEDGER_EVENTS.OCCURRED_AT, LEDGER_EVENTS.ID)
        .awaitList()
        .map { row ->
            CashPoolOperationRow(
                id = requireNotNull(row.get(LEDGER_EVENTS.ID)),
                type = requireNotNull(row.get(LEDGER_EVENTS.TYPE)),
                occurredAt = requireNotNull(row.get(LEDGER_EVENTS.OCCURRED_AT)).toInstant(),
                incomeAmountInCents = row.get(LEDGER_CASH_POOL_INCOME_EVENTS.AMOUNT_IN_CENTS),
                withdrawnBy = row.get(LEDGER_CASH_POOL_WITHDRAWAL_EVENTS.WITHDRAWN_BY),
                withdrawnAmountInCents = row.get(LEDGER_CASH_POOL_WITHDRAWAL_EVENTS.WITHDRAWN_AMOUNT_IN_CENTS),
                ownRevenueShareConsumedInCents =
                    row.get(LEDGER_CASH_POOL_WITHDRAWAL_EVENTS.OWN_REVENUE_SHARE_CONSUMED_IN_CENTS),
            )
        }

private suspend fun DSLContext.findIncomeAllocations(group: UUID): List<CashPoolIncomeAllocationRow> =
    select(
        LEDGER_MEMBER_CASH_POOL_SHARE_DELTAS.EVENT,
        LEDGER_MEMBER_CASH_POOL_SHARE_DELTAS.MEMBER_EMAIL,
        LEDGER_MEMBER_CASH_POOL_SHARE_DELTAS.AMOUNT_IN_CENTS,
    ).from(LEDGER_MEMBER_CASH_POOL_SHARE_DELTAS)
        .join(LEDGER_EVENTS)
        .on(LEDGER_EVENTS.ID.eq(LEDGER_MEMBER_CASH_POOL_SHARE_DELTAS.EVENT))
        .where(LEDGER_EVENTS.GROUP.eq(group))
        .and(LEDGER_EVENTS.TYPE.eq(LedgerEventType.CASH_POOL_INCOME))
        .orderBy(LEDGER_EVENTS.OCCURRED_AT, LEDGER_EVENTS.ID, LEDGER_MEMBER_CASH_POOL_SHARE_DELTAS.MEMBER_EMAIL)
        .awaitList()
        .map { row ->
            CashPoolIncomeAllocationRow(
                event = requireNotNull(row.get(LEDGER_MEMBER_CASH_POOL_SHARE_DELTAS.EVENT)),
                member = requireNotNull(row.get(LEDGER_MEMBER_CASH_POOL_SHARE_DELTAS.MEMBER_EMAIL)),
                amountInCents = requireNotNull(row.get(LEDGER_MEMBER_CASH_POOL_SHARE_DELTAS.AMOUNT_IN_CENTS)),
            )
        }

private fun CashPoolOperationRow.toSnapshot(allocations: List<CashPoolIncomeAllocationRow>): CashPoolOperationSnapshot =
    when (type) {
        LedgerEventType.CASH_POOL_INCOME ->
            CashPoolIncomeSnapshot(
                operation = id,
                occurredAt = occurredAt,
                amountInCents = requireNotNull(incomeAmountInCents),
                allocations =
                    allocations.map { allocation ->
                        CashPoolIncomeAllocationSnapshot(
                            member = allocation.member,
                            amountInCents = allocation.amountInCents,
                        )
                    },
            )

        LedgerEventType.CASH_POOL_WITHDRAWAL ->
            CashPoolWithdrawalSnapshot(
                operation = id,
                occurredAt = occurredAt,
                withdrawnBy = requireNotNull(withdrawnBy),
                amountInCents = requireNotNull(withdrawnAmountInCents),
                ownRevenueShareConsumedInCents = requireNotNull(ownRevenueShareConsumedInCents),
            )

        else -> error("unsupported cash-pool operation type $type")
    }

private data class CashPoolOperationRow(
    val id: UUID,
    val type: LedgerEventType,
    val occurredAt: Instant,
    val incomeAmountInCents: Long?,
    val withdrawnBy: String?,
    val withdrawnAmountInCents: Long?,
    val ownRevenueShareConsumedInCents: Long?,
)

private data class CashPoolIncomeAllocationRow(
    val event: UUID,
    val member: String,
    val amountInCents: Long,
)
