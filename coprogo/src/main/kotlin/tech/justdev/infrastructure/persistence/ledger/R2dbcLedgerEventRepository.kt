package tech.justdev.infrastructure.persistence.ledger

import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import kotlinx.coroutines.flow.collect
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
import java.util.UUID

@Singleton
open class R2dbcLedgerEventRepository(
    private val eventDataRepository: LedgerEventDataRepository,
    private val acceptedExpenseEventDataRepository: LedgerAcceptedExpenseEventDataRepository,
    private val cashPoolIncomeEventDataRepository: LedgerCashPoolIncomeEventDataRepository,
    private val cashPoolWithdrawalEventDataRepository: LedgerCashPoolWithdrawalEventDataRepository,
    private val transferDataRepository: LedgerMemberBalanceTransferDataRepository,
    private val cashPoolShareDeltaDataRepository: LedgerMemberCashPoolShareDeltaDataRepository,
) : LedgerEventRepository {
    @Transactional
    override suspend fun append(event: LedgerEvent) {
        eventDataRepository.persist(event.toHeaderEntity())
        event.persistDetail()
        transferDataRepository.saveAll(event.memberBalanceTransfers().map { transfer -> transfer.toEntity(event.id) }).collect()
        cashPoolShareDeltaDataRepository.saveAll(event.memberCashPoolShareDeltas().map { delta -> delta.toEntity(event.id) }).collect()
    }

    override suspend fun findByGroup(group: GroupId): List<LedgerEvent> {
        val groupId = group.toPrimitive()
        val transfersByEvent = transferDataRepository.findByGroup(groupId).groupBy { transfer -> transfer.eventId }
        val cashPoolShareDeltasByEvent =
            cashPoolShareDeltaDataRepository
                .findByGroup(groupId)
                .groupBy { delta -> delta.eventId }

        return eventDataRepository
            .findRowsByGroup(groupId)
            .map { row -> row.toDomain(transfersByEvent[row.id].orEmpty(), cashPoolShareDeltasByEvent[row.id].orEmpty()) }
    }

    private suspend fun LedgerEvent.persistDetail() {
        when (this) {
            is AcceptedExpenseLedgerEvent -> acceptedExpenseEventDataRepository.save(toDetailEntity())
            is CashPoolIncomeLedgerEvent -> cashPoolIncomeEventDataRepository.save(toDetailEntity())
            is CashPoolWithdrawalLedgerEvent -> cashPoolWithdrawalEventDataRepository.save(toDetailEntity())
        }
    }
}

private suspend fun LedgerEventDataRepository.persist(entity: LedgerEventEntity) {
    persist(
        id = entity.id,
        group = entity.group,
        eventType = entity.eventType,
        occurredAt = entity.occurredAt,
    )
}

private enum class LedgerEventType {
    ACCEPTED_EXPENSE,
    CASH_POOL_INCOME,
    CASH_POOL_WITHDRAWAL,
}

private fun LedgerEvent.toHeaderEntity(): LedgerEventEntity =
    when (this) {
        is AcceptedExpenseLedgerEvent -> baseEntity(type = LedgerEventType.ACCEPTED_EXPENSE)
        is CashPoolIncomeLedgerEvent -> baseEntity(type = LedgerEventType.CASH_POOL_INCOME)
        is CashPoolWithdrawalLedgerEvent -> baseEntity(type = LedgerEventType.CASH_POOL_WITHDRAWAL)
    }

private fun AcceptedExpenseLedgerEvent.toDetailEntity(): LedgerAcceptedExpenseEventEntity =
    LedgerAcceptedExpenseEventEntity(
        eventId = id.toPrimitive(),
        expense = expense.toPrimitive(),
        paidBy = paidBy.toPrimitive(),
    )

private fun CashPoolIncomeLedgerEvent.toDetailEntity(): LedgerCashPoolIncomeEventEntity =
    LedgerCashPoolIncomeEventEntity(
        eventId = id.toPrimitive(),
        amountInCents = amount.inCents(),
    )

private fun CashPoolWithdrawalLedgerEvent.toDetailEntity(): LedgerCashPoolWithdrawalEventEntity =
    LedgerCashPoolWithdrawalEventEntity(
        eventId = id.toPrimitive(),
        withdrawnBy = withdrawnBy.toPrimitive(),
        withdrawnAmountInCents = withdrawnAmount.inCents(),
        ownRevenueShareConsumedInCents = ownRevenueShareConsumed.inCents(),
    )

private fun LedgerEvent.baseEntity(type: LedgerEventType): LedgerEventEntity =
    LedgerEventEntity(
        id = id.toPrimitive(),
        group = group.toPrimitive(),
        eventType = type.name,
        occurredAt = occurredAt,
    )

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

private fun MemberBalanceTransfer.toEntity(event: LedgerEventId): LedgerMemberBalanceTransferEntity =
    LedgerMemberBalanceTransferEntity(
        id = UUID.randomUUID(),
        eventId = event.toPrimitive(),
        fromMember = fromMember.toPrimitive(),
        toMember = toMember.toPrimitive(),
        amountInCents = amount.inCents(),
    )

private fun MemberCashPoolShareDelta.toEntity(event: LedgerEventId): LedgerMemberCashPoolShareDeltaEntity =
    LedgerMemberCashPoolShareDeltaEntity(
        id = UUID.randomUUID(),
        eventId = event.toPrimitive(),
        memberEmail = member.toPrimitive(),
        amountInCents = amount.inCents(),
    )

private fun LedgerEventRow.toDomain(
    transfers: List<LedgerMemberBalanceTransferEntity>,
    cashPoolShareDeltas: List<LedgerMemberCashPoolShareDeltaEntity>,
): LedgerEvent =
    when (LedgerEventType.valueOf(eventType)) {
        LedgerEventType.ACCEPTED_EXPENSE ->
            AcceptedExpenseLedgerEvent(
                id = LedgerEventId(id),
                group = GroupId(group),
                expense = ExpenseId(requireNotNull(expense) { "accepted expense ledger event requires expense" }),
                paidBy = MemberEmail.of(requireNotNull(paidBy) { "accepted expense ledger event requires paidBy" }),
                occurredAt = occurredAt,
                transfers = transfers.map(LedgerMemberBalanceTransferEntity::toDomain).toSet(),
            )

        LedgerEventType.CASH_POOL_INCOME ->
            CashPoolIncomeLedgerEvent(
                id = LedgerEventId(id),
                group = GroupId(group),
                amount = MoneyAmount.ofCents(requireNotNull(incomeAmountInCents) { "cash pool income ledger event requires amount" }),
                allocations = cashPoolShareDeltas.map(LedgerMemberCashPoolShareDeltaEntity::toDomain).toSet(),
                occurredAt = occurredAt,
            )

        LedgerEventType.CASH_POOL_WITHDRAWAL ->
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
                balanceTransfers = transfers.map(LedgerMemberBalanceTransferEntity::toDomain).toSet(),
                occurredAt = occurredAt,
            )
    }

private fun LedgerMemberBalanceTransferEntity.toDomain(): MemberBalanceTransfer =
    MemberBalanceTransfer(
        fromMember = MemberEmail.of(fromMember),
        toMember = MemberEmail.of(toMember),
        amount = MoneyAmount.ofCents(amountInCents),
    )

private fun LedgerMemberCashPoolShareDeltaEntity.toDomain(): MemberCashPoolShareDelta =
    MemberCashPoolShareDelta(
        member = MemberEmail.of(memberEmail),
        amount = NetBalanceAmount.ofCents(amountInCents),
    )
