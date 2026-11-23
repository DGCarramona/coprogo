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
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.ledger.effect.MemberCashPoolShareDelta
import tech.justdev.domain.ledger.event.CashPoolIncomeLedgerEvent
import tech.justdev.domain.ledger.event.CashPoolWithdrawalLedgerEvent
import tech.justdev.domain.ledger.event.LedgerEvent
import tech.justdev.domain.ledger.projection.replayCashPoolChronologically
import tech.justdev.domain.ledger.valueobject.LedgerEventId
import tech.justdev.domain.ledger.valueobject.NetBalanceAmount
import tech.justdev.domain.shared.money.MoneyAmount
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
        val operations = dsl.findCashPoolOperations(group.toPrimitive())
        val allocationsByIncome = dsl.findIncomeAllocations(group.toPrimitive()).groupBy { allocation -> allocation.event }
        val replayed =
            operations
                .map { operation -> operation.toDomain(group, allocationsByIncome[operation.id].orEmpty()) }
                .replayCashPoolChronologically()
                .associateBy { event -> event.id.toPrimitive() }

        return operations.map { operation -> replayed.getValue(operation.id).toSnapshot() }
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

private fun CashPoolOperationRow.toDomain(
    group: GroupId,
    allocations: List<CashPoolIncomeAllocationRow>,
): LedgerEvent =
    when (type) {
        LedgerEventType.CASH_POOL_INCOME ->
            CashPoolIncomeLedgerEvent(
                id = LedgerEventId(id),
                group = group,
                occurredAt = occurredAt,
                amount = MoneyAmount.ofCents(requireNotNull(incomeAmountInCents)),
                allocations =
                    allocations
                        .map { allocation ->
                            MemberCashPoolShareDelta(
                                member = MemberEmail.of(allocation.member),
                                amount = NetBalanceAmount.ofCents(allocation.amountInCents),
                            )
                        }.toSet(),
            )

        LedgerEventType.CASH_POOL_WITHDRAWAL ->
            CashPoolWithdrawalLedgerEvent(
                id = LedgerEventId(id),
                group = group,
                occurredAt = occurredAt,
                withdrawnBy = MemberEmail.of(requireNotNull(withdrawnBy)),
                withdrawnAmount = MoneyAmount.ofCents(requireNotNull(withdrawnAmountInCents)),
                ownRevenueShareConsumed = MoneyAmount.ofCents(requireNotNull(withdrawnAmountInCents)),
                balanceTransfers = emptySet(),
            )

        else -> error("unsupported cash-pool operation type $type")
    }

private fun LedgerEvent.toSnapshot(): CashPoolOperationSnapshot =
    when (this) {
        is CashPoolIncomeLedgerEvent ->
            CashPoolIncomeSnapshot(
                operation = id.toPrimitive(),
                occurredAt = occurredAt,
                amountInCents = amount.inCents(),
                allocations =
                    allocations
                        .map { allocation ->
                            CashPoolIncomeAllocationSnapshot(allocation.member.toPrimitive(), allocation.amount.inCents())
                        }.sortedBy { allocation -> allocation.member },
            )

        is CashPoolWithdrawalLedgerEvent ->
            CashPoolWithdrawalSnapshot(
                operation = id.toPrimitive(),
                occurredAt = occurredAt,
                withdrawnBy = withdrawnBy.toPrimitive(),
                amountInCents = withdrawnAmount.inCents(),
                ownRevenueShareConsumedInCents = ownRevenueShareConsumed.inCents(),
            )

        else -> error("unsupported cash-pool operation")
    }

private data class CashPoolOperationRow(
    val id: UUID,
    val type: LedgerEventType,
    val occurredAt: Instant,
    val incomeAmountInCents: Long?,
    val withdrawnBy: String?,
    val withdrawnAmountInCents: Long?,
)

private data class CashPoolIncomeAllocationRow(
    val event: UUID,
    val member: String,
    val amountInCents: Long,
)
