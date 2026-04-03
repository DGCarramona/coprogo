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
    val fromMember: MemberId,
    val toMember: MemberId,
    val amount: MoneyAmount,
) {
    init {
        require(fromMember != toMember) { "transfer participants must be different" }
        require(amount > MoneyAmount.ZERO) { "transfer amount must be > 0" }
    }
}

sealed interface LedgerEvent {
    val id: LedgerEventId
    val group: GroupId
    val occurredAt: Instant
    val transfers: Set<LedgerTransfer>
}

data class AcceptedExpenseLedgerEvent(
    override val id: LedgerEventId,
    override val group: GroupId,
    val expense: ExpenseId,
    val paidBy: MemberId,
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
                .filter { participation -> participation.member != expense.createdBy }
                .map { participation ->
                    LedgerTransfer(
                        fromMember = participation.member,
                        toMember = expense.createdBy,
                        amount = participation.amount,
                    )
                }
                .toSet()

            if (transfers.isEmpty()) {
                return null
            }

            return AcceptedExpenseLedgerEvent(
                id = LedgerEventId.fromName("accepted-expense:${expense.id.value}"),
                group = expense.group,
                expense = expense.id,
                paidBy = expense.createdBy,
                occurredAt = expense.acceptedAt(),
                transfers = transfers,
            )
        }
    }
}
