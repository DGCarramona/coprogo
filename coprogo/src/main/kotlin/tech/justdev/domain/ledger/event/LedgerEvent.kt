package tech.justdev.domain.ledger.event

import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.expense.valueobject.ExpenseStatus
import tech.justdev.domain.ledger.effect.CashPoolBalanceDelta
import tech.justdev.domain.ledger.effect.LedgerEffect
import tech.justdev.domain.ledger.effect.MemberBalanceTransfer
import tech.justdev.domain.ledger.effect.MemberCashPoolShareDelta
import tech.justdev.domain.ledger.valueobject.LedgerEventId
import tech.justdev.domain.revenue.valueobject.RevenueDistribution
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.money.sum
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.domain.shared.valueobject.MemberId
import java.time.Instant

sealed interface LedgerEvent {
    val id: LedgerEventId
    val group: GroupId
    val occurredAt: Instant
    val effects: Set<LedgerEffect>
}

data class AcceptedExpenseLedgerEvent(
    override val id: LedgerEventId,
    override val group: GroupId,
    val expense: ExpenseId,
    val paidBy: MemberId,
    override val occurredAt: Instant,
    val transfers: Set<MemberBalanceTransfer>,
) : LedgerEvent {
    override val effects: Set<LedgerEffect>
        get() = transfers

    companion object {
        fun from(expense: Expense): AcceptedExpenseLedgerEvent? {
            require(expense.status == ExpenseStatus.ACCEPTED) {
                "expense must be accepted before it can produce a ledger event"
            }

            val transfers =
                expense.participations
                    .asSequence()
                    .filter { participation -> participation.member != expense.createdBy }
                    .map { participation ->
                        MemberBalanceTransfer(
                            fromMember = participation.member,
                            toMember = expense.createdBy,
                            amount = participation.amount,
                        )
                    }.toSet()

            if (transfers.isEmpty()) {
                return null
            }

            return AcceptedExpenseLedgerEvent(
                id = LedgerEventId.fromName("accepted-expense:${expense.id.toPrimitive()}"),
                group = expense.group,
                expense = expense.id,
                paidBy = expense.createdBy,
                occurredAt = expense.acceptedAt(),
                transfers = transfers,
            )
        }
    }
}

data class CashPoolIncomeLedgerEvent(
    override val id: LedgerEventId,
    override val group: GroupId,
    val amount: MoneyAmount,
    override val occurredAt: Instant,
) : LedgerEvent {
    init {
        require(amount > MoneyAmount.ZERO) { "cash pool income amount must be > 0" }
    }

    override val effects: Set<LedgerEffect>
        get() = setOf(CashPoolBalanceDelta.increase(amount))
}

data class RevenueDistributionLedgerEvent(
    override val id: LedgerEventId,
    override val group: GroupId,
    val totalAmount: MoneyAmount,
    val allocations: Set<MemberCashPoolShareDelta>,
    override val occurredAt: Instant,
) : LedgerEvent {
    init {
        require(totalAmount > MoneyAmount.ZERO) { "revenue distribution totalAmount must be > 0" }
        require(allocations.isNotEmpty()) { "revenue distribution allocations must not be empty" }
        require(allocations.map { allocation -> allocation.member }.toSet().size == allocations.size) {
            "revenue distribution allocations must contain unique members"
        }
        require(allocations.all { allocation -> allocation.amount.inCents() > 0 }) {
            "revenue distribution allocations must all be positive"
        }
        require(allocations.sumOf { allocation -> allocation.amount.inCents() } == totalAmount.inCents()) {
            "revenue distribution allocations must add up to totalAmount"
        }
    }

    override val effects: Set<LedgerEffect>
        get() = allocations

    companion object {
        fun from(
            id: LedgerEventId,
            group: GroupId,
            occurredAt: Instant,
            distribution: RevenueDistribution,
        ): RevenueDistributionLedgerEvent =
            RevenueDistributionLedgerEvent(
                id = id,
                group = group,
                totalAmount = distribution.totalAmount,
                allocations =
                    distribution.allocations
                        .map { allocation ->
                            MemberCashPoolShareDelta.increase(
                                member = allocation.member,
                                amount = allocation.amount,
                            )
                        }.toSet(),
                occurredAt = occurredAt,
            )
    }
}

data class CashPoolWithdrawalLedgerEvent(
    override val id: LedgerEventId,
    override val group: GroupId,
    val withdrawnBy: MemberId,
    val withdrawnAmount: MoneyAmount,
    val ownRevenueShareConsumed: MoneyAmount,
    val balanceTransfers: Set<MemberBalanceTransfer>,
    override val occurredAt: Instant,
) : LedgerEvent {
    init {
        require(withdrawnAmount > MoneyAmount.ZERO) { "cash pool withdrawal amount must be > 0" }
        require(ownRevenueShareConsumed <= withdrawnAmount) {
            "consumed own revenue share must be <= withdrawn amount"
        }

        val overWithdrawalAmount = withdrawnAmount - ownRevenueShareConsumed
        require(balanceTransfers.all { transfer -> transfer.fromMember == withdrawnBy }) {
            "cash pool over-withdrawal compensation transfers must originate from the withdrawing member"
        }
        require(balanceTransfers.map { transfer -> transfer.amount }.sum() == overWithdrawalAmount) {
            "cash pool over-withdrawal compensation transfers must add up to the over-withdrawn amount"
        }
    }

    override val effects: Set<LedgerEffect>
        get() =
            buildSet {
                add(CashPoolBalanceDelta.decrease(withdrawnAmount))
                if (ownRevenueShareConsumed > MoneyAmount.ZERO) {
                    add(MemberCashPoolShareDelta.decrease(member = withdrawnBy, amount = ownRevenueShareConsumed))
                }
                addAll(balanceTransfers)
            }
}
