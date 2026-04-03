package tech.justdev.domain.ledger.event

import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.expense.valueobject.ExpenseStatus
import tech.justdev.domain.ledger.valueobject.LedgerEventId
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.domain.shared.valueobject.MemberId
import tech.justdev.domain.shared.money.MoneyAmount
import java.time.Instant

data class LedgerTransfer(
    val fromMemberId: MemberId,
    val toMemberId: MemberId,
    val amount: MoneyAmount,
) {
    init {
        require(fromMemberId != toMemberId) { "transfer participants must be different" }
        require(amount > MoneyAmount.ZERO) { "transfer amount must be > 0" }
    }
}

sealed interface LedgerEvent {
    val eventId: LedgerEventId
    val groupId: GroupId
    val occurredAt: Instant
    val transfers: Set<LedgerTransfer>
}

data class AcceptedExpenseLedgerEvent(
    override val eventId: LedgerEventId,
    override val groupId: GroupId,
    val expenseId: ExpenseId,
    val paidByMemberId: MemberId,
    override val occurredAt: Instant,
    override val transfers: Set<LedgerTransfer>,
) : LedgerEvent {

    companion object {
        fun from(expense: Expense): AcceptedExpenseLedgerEvent? {
            require(expense.status == ExpenseStatus.ACCEPTED) {
                "expense must be accepted before it can produce a ledger event"
            }

            val transfers = expense.participations
                .asSequence()
                .filter { participation -> participation.memberId != expense.createdByMemberId }
                .map { participation ->
                    LedgerTransfer(
                        fromMemberId = participation.memberId,
                        toMemberId = expense.createdByMemberId,
                        amount = participation.amount,
                    )
                }
                .toSet()

            if (transfers.isEmpty()) {
                return null
            }

            return AcceptedExpenseLedgerEvent(
                eventId = LedgerEventId("expense:${expense.id.value}:accepted"),
                groupId = expense.groupId,
                expenseId = expense.id,
                paidByMemberId = expense.createdByMemberId,
                occurredAt = expense.acceptedAt(),
                transfers = transfers,
            )
        }
    }
}
