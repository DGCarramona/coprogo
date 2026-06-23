package tech.justdev.infrastructure.persistence.ledger

import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.Record
import org.jooq.ResultQuery
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.ledger.effect.MemberBalanceTransfer
import tech.justdev.domain.ledger.effect.MemberCashPoolShareDelta
import tech.justdev.domain.ledger.event.AcceptedExpenseLedgerEvent
import tech.justdev.domain.ledger.event.CashPoolIncomeLedgerEvent
import tech.justdev.domain.ledger.event.CashPoolWithdrawalLedgerEvent
import tech.justdev.domain.ledger.event.LedgerEvent
import tech.justdev.domain.ledger.repository.LedgerEventRepository
import tech.justdev.domain.ledger.valueobject.LedgerEventId
import tech.justdev.domain.ledger.valueobject.NetBalanceAmount
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.infrastructure.persistence.jooq.Tables.LEDGER_ACCEPTED_EXPENSE_EVENTS
import tech.justdev.infrastructure.persistence.jooq.Tables.LEDGER_CASH_POOL_INCOME_EVENTS
import tech.justdev.infrastructure.persistence.jooq.Tables.LEDGER_CASH_POOL_WITHDRAWAL_EVENTS
import tech.justdev.infrastructure.persistence.jooq.Tables.LEDGER_EVENTS
import tech.justdev.infrastructure.persistence.jooq.Tables.LEDGER_MEMBER_BALANCE_TRANSFERS
import tech.justdev.infrastructure.persistence.jooq.Tables.LEDGER_MEMBER_CASH_POOL_SHARE_DELTAS
import tech.justdev.infrastructure.persistence.jooq.dsl
import tech.justdev.infrastructure.persistence.jooq.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import tech.justdev.infrastructure.persistence.jooq.enums.LedgerEventType as JooqLedgerEventType

@Singleton
open class R2dbcLedgerEventRepository(
    @Named("default")
    private val connectionFactory: ConnectionFactory,
) : LedgerEventRepository {
    override suspend fun append(event: LedgerEvent) {
        connectionFactory.transaction {
            appendInTransaction(event)
        }
    }

    private suspend fun appendInTransaction(event: LedgerEvent) {
        val dsl = connectionFactory.dsl()

        dsl.persistHeader(event)
        dsl.persistDetail(event)
        event.memberBalanceTransfers().forEach { transfer -> dsl.persist(transfer, event.id) }
        event.memberCashPoolShareDeltas().forEach { delta -> dsl.persist(delta, event.id) }
    }

    override suspend fun findByGroup(group: GroupId): List<LedgerEvent> {
        val dsl = connectionFactory.dsl()
        val groupId = group.toPrimitive()
        val transfersByEvent = dsl.findTransfersByGroup(groupId).groupBy { transfer -> transfer.event }
        val cashPoolShareDeltasByEvent = dsl.findCashPoolShareDeltasByGroup(groupId).groupBy { delta -> delta.event }

        return dsl
            .findRowsByGroup(groupId)
            .map { row -> row.toDomain(transfersByEvent[row.id].orEmpty(), cashPoolShareDeltasByEvent[row.id].orEmpty()) }
    }
}

private suspend fun org.jooq.DSLContext.persistHeader(event: LedgerEvent) {
    insertInto(LEDGER_EVENTS)
        .columns(LEDGER_EVENTS.ID, LEDGER_EVENTS.GROUP, LEDGER_EVENTS.TYPE, LEDGER_EVENTS.OCCURRED_AT)
        .values(event.id.toPrimitive(), event.group.toPrimitive(), event.toJooqType(), event.occurredAt.atOffset(ZoneOffset.UTC))
        .awaitFirstOrNull()
}

private suspend fun org.jooq.DSLContext.persistDetail(event: LedgerEvent) {
    when (event) {
        is AcceptedExpenseLedgerEvent ->
            insertInto(LEDGER_ACCEPTED_EXPENSE_EVENTS)
                .columns(
                    LEDGER_ACCEPTED_EXPENSE_EVENTS.EVENT,
                    LEDGER_ACCEPTED_EXPENSE_EVENTS.EXPENSE,
                    LEDGER_ACCEPTED_EXPENSE_EVENTS.PAID_BY,
                ).values(event.id.toPrimitive(), event.expense.toPrimitive(), event.paidBy.toPrimitive())
                .awaitFirstOrNull()

        is CashPoolIncomeLedgerEvent ->
            insertInto(LEDGER_CASH_POOL_INCOME_EVENTS)
                .columns(LEDGER_CASH_POOL_INCOME_EVENTS.EVENT, LEDGER_CASH_POOL_INCOME_EVENTS.AMOUNT_IN_CENTS)
                .values(event.id.toPrimitive(), event.amount.inCents())
                .awaitFirstOrNull()

        is CashPoolWithdrawalLedgerEvent ->
            insertInto(LEDGER_CASH_POOL_WITHDRAWAL_EVENTS)
                .columns(
                    LEDGER_CASH_POOL_WITHDRAWAL_EVENTS.EVENT,
                    LEDGER_CASH_POOL_WITHDRAWAL_EVENTS.WITHDRAWN_BY,
                    LEDGER_CASH_POOL_WITHDRAWAL_EVENTS.WITHDRAWN_AMOUNT_IN_CENTS,
                    LEDGER_CASH_POOL_WITHDRAWAL_EVENTS.OWN_REVENUE_SHARE_CONSUMED_IN_CENTS,
                ).values(
                    event.id.toPrimitive(),
                    event.withdrawnBy.toPrimitive(),
                    event.withdrawnAmount.inCents(),
                    event.ownRevenueShareConsumed.inCents(),
                ).awaitFirstOrNull()
    }
}

private suspend fun org.jooq.DSLContext.persist(
    transfer: MemberBalanceTransfer,
    event: LedgerEventId,
) {
    insertInto(LEDGER_MEMBER_BALANCE_TRANSFERS)
        .columns(
            LEDGER_MEMBER_BALANCE_TRANSFERS.ID,
            LEDGER_MEMBER_BALANCE_TRANSFERS.EVENT,
            LEDGER_MEMBER_BALANCE_TRANSFERS.FROM_MEMBER,
            LEDGER_MEMBER_BALANCE_TRANSFERS.TO_MEMBER,
            LEDGER_MEMBER_BALANCE_TRANSFERS.AMOUNT_IN_CENTS,
        ).values(
            UUID.randomUUID(),
            event.toPrimitive(),
            transfer.fromMember.toPrimitive(),
            transfer.toMember.toPrimitive(),
            transfer.amount.inCents(),
        ).awaitFirstOrNull()
}

private suspend fun org.jooq.DSLContext.persist(
    delta: MemberCashPoolShareDelta,
    event: LedgerEventId,
) {
    insertInto(LEDGER_MEMBER_CASH_POOL_SHARE_DELTAS)
        .columns(
            LEDGER_MEMBER_CASH_POOL_SHARE_DELTAS.ID,
            LEDGER_MEMBER_CASH_POOL_SHARE_DELTAS.EVENT,
            LEDGER_MEMBER_CASH_POOL_SHARE_DELTAS.MEMBER_EMAIL,
            LEDGER_MEMBER_CASH_POOL_SHARE_DELTAS.AMOUNT_IN_CENTS,
        ).values(UUID.randomUUID(), event.toPrimitive(), delta.member.toPrimitive(), delta.amount.inCents())
        .awaitFirstOrNull()
}

private suspend fun org.jooq.DSLContext.findRowsByGroup(group: UUID): List<LedgerEventRow> =
    select(
        LEDGER_EVENTS.ID,
        LEDGER_EVENTS.GROUP,
        LEDGER_EVENTS.TYPE,
        LEDGER_EVENTS.OCCURRED_AT,
        LEDGER_ACCEPTED_EXPENSE_EVENTS.EXPENSE,
        LEDGER_ACCEPTED_EXPENSE_EVENTS.PAID_BY,
        LEDGER_CASH_POOL_INCOME_EVENTS.AMOUNT_IN_CENTS,
        LEDGER_CASH_POOL_WITHDRAWAL_EVENTS.WITHDRAWN_BY,
        LEDGER_CASH_POOL_WITHDRAWAL_EVENTS.WITHDRAWN_AMOUNT_IN_CENTS,
        LEDGER_CASH_POOL_WITHDRAWAL_EVENTS.OWN_REVENUE_SHARE_CONSUMED_IN_CENTS,
    ).from(LEDGER_EVENTS)
        .leftJoin(LEDGER_ACCEPTED_EXPENSE_EVENTS)
        .on(LEDGER_ACCEPTED_EXPENSE_EVENTS.EVENT.eq(LEDGER_EVENTS.ID))
        .leftJoin(LEDGER_CASH_POOL_INCOME_EVENTS)
        .on(LEDGER_CASH_POOL_INCOME_EVENTS.EVENT.eq(LEDGER_EVENTS.ID))
        .leftJoin(LEDGER_CASH_POOL_WITHDRAWAL_EVENTS)
        .on(LEDGER_CASH_POOL_WITHDRAWAL_EVENTS.EVENT.eq(LEDGER_EVENTS.ID))
        .where(LEDGER_EVENTS.GROUP.eq(group))
        .orderBy(LEDGER_EVENTS.OCCURRED_AT, LEDGER_EVENTS.ID)
        .awaitList()
        .map { row ->
            LedgerEventRow(
                id = row.get(LEDGER_EVENTS.ID),
                group = row.get(LEDGER_EVENTS.GROUP),
                type = row.get(LEDGER_EVENTS.TYPE),
                occurredAt = row.get(LEDGER_EVENTS.OCCURRED_AT),
                expense = row.get(LEDGER_ACCEPTED_EXPENSE_EVENTS.EXPENSE),
                paidBy = row.get(LEDGER_ACCEPTED_EXPENSE_EVENTS.PAID_BY),
                incomeAmountInCents = row.get(LEDGER_CASH_POOL_INCOME_EVENTS.AMOUNT_IN_CENTS),
                withdrawnBy = row.get(LEDGER_CASH_POOL_WITHDRAWAL_EVENTS.WITHDRAWN_BY),
                withdrawnAmountInCents = row.get(LEDGER_CASH_POOL_WITHDRAWAL_EVENTS.WITHDRAWN_AMOUNT_IN_CENTS),
                ownRevenueShareConsumedInCents = row.get(LEDGER_CASH_POOL_WITHDRAWAL_EVENTS.OWN_REVENUE_SHARE_CONSUMED_IN_CENTS),
            )
        }

private suspend fun org.jooq.DSLContext.findTransfersByGroup(group: UUID): List<LedgerTransferRow> =
    select(
        LEDGER_MEMBER_BALANCE_TRANSFERS.EVENT,
        LEDGER_MEMBER_BALANCE_TRANSFERS.FROM_MEMBER,
        LEDGER_MEMBER_BALANCE_TRANSFERS.TO_MEMBER,
        LEDGER_MEMBER_BALANCE_TRANSFERS.AMOUNT_IN_CENTS,
    ).from(LEDGER_MEMBER_BALANCE_TRANSFERS)
        .join(LEDGER_EVENTS)
        .on(LEDGER_EVENTS.ID.eq(LEDGER_MEMBER_BALANCE_TRANSFERS.EVENT))
        .where(LEDGER_EVENTS.GROUP.eq(group))
        .orderBy(
            LEDGER_EVENTS.OCCURRED_AT,
            LEDGER_MEMBER_BALANCE_TRANSFERS.FROM_MEMBER,
            LEDGER_MEMBER_BALANCE_TRANSFERS.TO_MEMBER,
            LEDGER_MEMBER_BALANCE_TRANSFERS.AMOUNT_IN_CENTS,
        ).awaitList()
        .map { row ->
            LedgerTransferRow(
                event = row.get(LEDGER_MEMBER_BALANCE_TRANSFERS.EVENT),
                fromMember = row.get(LEDGER_MEMBER_BALANCE_TRANSFERS.FROM_MEMBER),
                toMember = row.get(LEDGER_MEMBER_BALANCE_TRANSFERS.TO_MEMBER),
                amountInCents = row.get(LEDGER_MEMBER_BALANCE_TRANSFERS.AMOUNT_IN_CENTS),
            )
        }

private suspend fun org.jooq.DSLContext.findCashPoolShareDeltasByGroup(group: UUID): List<LedgerCashPoolShareDeltaRow> =
    select(
        LEDGER_MEMBER_CASH_POOL_SHARE_DELTAS.EVENT,
        LEDGER_MEMBER_CASH_POOL_SHARE_DELTAS.MEMBER_EMAIL,
        LEDGER_MEMBER_CASH_POOL_SHARE_DELTAS.AMOUNT_IN_CENTS,
    ).from(LEDGER_MEMBER_CASH_POOL_SHARE_DELTAS)
        .join(LEDGER_EVENTS)
        .on(LEDGER_EVENTS.ID.eq(LEDGER_MEMBER_CASH_POOL_SHARE_DELTAS.EVENT))
        .where(LEDGER_EVENTS.GROUP.eq(group))
        .orderBy(
            LEDGER_EVENTS.OCCURRED_AT,
            LEDGER_MEMBER_CASH_POOL_SHARE_DELTAS.MEMBER_EMAIL,
            LEDGER_MEMBER_CASH_POOL_SHARE_DELTAS.AMOUNT_IN_CENTS,
        ).awaitList()
        .map { row ->
            LedgerCashPoolShareDeltaRow(
                event = row.get(LEDGER_MEMBER_CASH_POOL_SHARE_DELTAS.EVENT),
                memberEmail = row.get(LEDGER_MEMBER_CASH_POOL_SHARE_DELTAS.MEMBER_EMAIL),
                amountInCents = row.get(LEDGER_MEMBER_CASH_POOL_SHARE_DELTAS.AMOUNT_IN_CENTS),
            )
        }

private fun LedgerEvent.toJooqType(): JooqLedgerEventType =
    when (this) {
        is AcceptedExpenseLedgerEvent -> JooqLedgerEventType.ACCEPTED_EXPENSE
        is CashPoolIncomeLedgerEvent -> JooqLedgerEventType.CASH_POOL_INCOME
        is CashPoolWithdrawalLedgerEvent -> JooqLedgerEventType.CASH_POOL_WITHDRAWAL
    }

private fun LedgerEvent.memberBalanceTransfers(): Set<MemberBalanceTransfer> =
    when (this) {
        is AcceptedExpenseLedgerEvent -> transfers
        is CashPoolWithdrawalLedgerEvent -> balanceTransfers
        is CashPoolIncomeLedgerEvent -> emptySet()
    }

private fun LedgerEvent.memberCashPoolShareDeltas(): Set<MemberCashPoolShareDelta> =
    when (this) {
        is AcceptedExpenseLedgerEvent -> emptySet()
        is CashPoolIncomeLedgerEvent -> allocations
        is CashPoolWithdrawalLedgerEvent ->
            if (ownRevenueShareConsumed.isZero()) {
                emptySet()
            } else {
                setOf(MemberCashPoolShareDelta.decrease(withdrawnBy, ownRevenueShareConsumed))
            }
    }

private fun LedgerEventRow.toDomain(
    transfers: List<LedgerTransferRow>,
    cashPoolShareDeltas: List<LedgerCashPoolShareDeltaRow>,
): LedgerEvent =
    when (type) {
        JooqLedgerEventType.ACCEPTED_EXPENSE ->
            AcceptedExpenseLedgerEvent(
                id = LedgerEventId(id),
                group = GroupId(group),
                expense = ExpenseId(requireNotNull(expense) { "accepted expense ledger event requires expense" }),
                paidBy = MemberEmail.of(requireNotNull(paidBy) { "accepted expense ledger event requires paidBy" }),
                occurredAt = occurredAt.toInstant(),
                transfers = transfers.map { transfer -> transfer.toDomain() }.toSet(),
            )

        JooqLedgerEventType.CASH_POOL_INCOME ->
            CashPoolIncomeLedgerEvent(
                id = LedgerEventId(id),
                group = GroupId(group),
                amount = MoneyAmount.ofCents(requireNotNull(incomeAmountInCents) { "cash pool income ledger event requires amount" }),
                allocations = cashPoolShareDeltas.map { delta -> delta.toDomain() }.toSet(),
                occurredAt = occurredAt.toInstant(),
            )

        JooqLedgerEventType.CASH_POOL_WITHDRAWAL ->
            CashPoolWithdrawalLedgerEvent(
                id = LedgerEventId(id),
                group = GroupId(group),
                withdrawnBy = MemberEmail.of(requireNotNull(withdrawnBy) { "cash pool withdrawal ledger event requires withdrawnBy" }),
                withdrawnAmount =
                    MoneyAmount.ofCents(
                        requireNotNull(withdrawnAmountInCents) { "cash pool withdrawal ledger event requires withdrawnAmount" },
                    ),
                ownRevenueShareConsumed =
                    MoneyAmount.ofCents(
                        requireNotNull(ownRevenueShareConsumedInCents) {
                            "cash pool withdrawal ledger event requires ownRevenueShareConsumed"
                        },
                    ),
                balanceTransfers = transfers.map { transfer -> transfer.toDomain() }.toSet(),
                occurredAt = occurredAt.toInstant(),
            )
    }

private fun LedgerTransferRow.toDomain(): MemberBalanceTransfer =
    MemberBalanceTransfer(
        fromMember = MemberEmail.of(fromMember),
        toMember = MemberEmail.of(toMember),
        amount = MoneyAmount.ofCents(amountInCents),
    )

private fun LedgerCashPoolShareDeltaRow.toDomain(): MemberCashPoolShareDelta =
    MemberCashPoolShareDelta(
        member = MemberEmail.of(memberEmail),
        amount = NetBalanceAmount.ofCents(amountInCents),
    )

private suspend fun <R : Record> ResultQuery<R>.awaitList(): List<R> = asFlow().toList()

private data class LedgerEventRow(
    val id: UUID,
    val group: UUID,
    val type: JooqLedgerEventType,
    val occurredAt: OffsetDateTime,
    val expense: UUID?,
    val paidBy: String?,
    val incomeAmountInCents: Long?,
    val withdrawnBy: String?,
    val withdrawnAmountInCents: Long?,
    val ownRevenueShareConsumedInCents: Long?,
)

private data class LedgerTransferRow(
    val event: UUID,
    val fromMember: String,
    val toMember: String,
    val amountInCents: Long,
)

private data class LedgerCashPoolShareDeltaRow(
    val event: UUID,
    val memberEmail: String,
    val amountInCents: Long,
)
