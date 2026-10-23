package tech.justdev.domain.ledger.event

import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.expense.valueobject.ExpenseStatus
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.ledger.effect.CashPoolBalanceDelta
import tech.justdev.domain.ledger.effect.LedgerEffect
import tech.justdev.domain.ledger.effect.MemberBalanceTransfer
import tech.justdev.domain.ledger.effect.MemberCashPoolShareDelta
import tech.justdev.domain.ledger.projection.MemberCashPoolShareBalance
import tech.justdev.domain.ledger.valueobject.LedgerEventId
import tech.justdev.domain.reimbursement.entity.Reimbursement
import tech.justdev.domain.reimbursement.valueobject.ReimbursementId
import tech.justdev.domain.reimbursement.valueobject.ReimbursementStatus
import tech.justdev.domain.revenue.valueobject.RevenueDistribution
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.money.sum
import tech.justdev.domain.shared.valueobject.GroupId
import java.math.BigInteger
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
    val paidBy: MemberEmail,
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

data class AcceptedReimbursementLedgerEvent(
    override val id: LedgerEventId,
    override val group: GroupId,
    val reimbursement: ReimbursementId,
    override val occurredAt: Instant,
    val balanceTransfer: MemberBalanceTransfer,
) : LedgerEvent {
    override val effects: Set<LedgerEffect>
        get() = setOf(balanceTransfer)

    companion object {
        fun from(reimbursement: Reimbursement): AcceptedReimbursementLedgerEvent {
            require(reimbursement.status is ReimbursementStatus.Accepted) {
                "reimbursement must be accepted before it can produce a ledger event"
            }

            return AcceptedReimbursementLedgerEvent(
                id = LedgerEventId.fromName("accepted-reimbursement:${reimbursement.id.toPrimitive()}"),
                group = reimbursement.group,
                reimbursement = reimbursement.id,
                occurredAt = reimbursement.status.acceptedAt,
                balanceTransfer =
                    MemberBalanceTransfer(
                        fromMember = reimbursement.receivedBy,
                        toMember = reimbursement.paidBy,
                        amount = reimbursement.amount,
                    ),
            )
        }
    }
}

data class CashPoolIncomeLedgerEvent(
    override val id: LedgerEventId,
    override val group: GroupId,
    val amount: MoneyAmount,
    val allocations: Set<MemberCashPoolShareDelta>,
    override val occurredAt: Instant,
) : LedgerEvent {
    init {
        require(amount > MoneyAmount.ZERO) { "cash pool income amount must be > 0" }
        require(allocations.isNotEmpty()) { "cash pool income allocations must not be empty" }
        require(allocations.map { allocation -> allocation.member }.toSet().size == allocations.size) {
            "cash pool income allocations must contain unique members"
        }
        require(allocations.all { allocation -> allocation.amount.inCents() > 0 }) {
            "cash pool income allocations must all be positive"
        }
        require(allocations.sumOf { allocation -> allocation.amount.inCents() } == amount.inCents()) {
            "cash pool income allocations must add up to amount"
        }
    }

    override val effects: Set<LedgerEffect>
        get() = setOf(CashPoolBalanceDelta.increase(amount)) + allocations

    companion object {
        fun from(
            id: LedgerEventId,
            group: GroupId,
            occurredAt: Instant,
            distribution: RevenueDistribution,
        ): CashPoolIncomeLedgerEvent =
            CashPoolIncomeLedgerEvent(
                id = id,
                group = group,
                amount = distribution.totalAmount,
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
    val withdrawnBy: MemberEmail,
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

    companion object {
        fun from(
            id: LedgerEventId,
            group: GroupId,
            withdrawnBy: MemberEmail,
            withdrawnAmount: MoneyAmount,
            occurredAt: Instant,
            memberCashPoolShares: Set<MemberCashPoolShareBalance>,
        ): CashPoolWithdrawalLedgerEvent {
            val ownRevenueShareConsumed =
                memberCashPoolShares
                    .firstOrNull { share -> share.member == withdrawnBy }
                    ?.amount
                    ?.inCents()
                    ?.coerceAtLeast(0)
                    ?.coerceAtMost(withdrawnAmount.inCents())
                    ?.let(MoneyAmount::ofCents)
                    ?: MoneyAmount.ZERO

            return CashPoolWithdrawalLedgerEvent(
                id = id,
                group = group,
                withdrawnBy = withdrawnBy,
                withdrawnAmount = withdrawnAmount,
                ownRevenueShareConsumed = ownRevenueShareConsumed,
                balanceTransfers =
                    allocateCompensationTransfers(
                        withdrawnBy = withdrawnBy,
                        amount = withdrawnAmount - ownRevenueShareConsumed,
                        memberCashPoolShares = memberCashPoolShares,
                    ),
                occurredAt = occurredAt,
            )
        }
    }
}

private fun allocateCompensationTransfers(
    withdrawnBy: MemberEmail,
    amount: MoneyAmount,
    memberCashPoolShares: Set<MemberCashPoolShareBalance>,
): Set<MemberBalanceTransfer> {
    if (amount.isZero()) return emptySet()

    val weights =
        memberCashPoolShares
            .asSequence()
            .filter { share -> share.member != withdrawnBy && share.amount.inCents() > 0 }
            .map { share -> CompensationWeight(share.member, share.amount.inCents()) }
            .toList()
    val totalWeight = weights.fold(BigInteger.ZERO) { total, weight -> total + weight.amount.toBigInteger() }
    require(totalWeight >= amount.inCents().toBigInteger()) {
        "positive cash pool shares of other members must cover the over-withdrawn amount"
    }

    val allocations =
        weights.map { weight ->
            val (quotient, remainder) =
                amount
                    .inCents()
                    .toBigInteger()
                    .multiply(weight.amount.toBigInteger())
                    .divideAndRemainder(totalWeight)
            CompensationAllocation(
                member = weight.member,
                amountInCents = quotient.longValueExact(),
                remainder = remainder,
            )
        }
    val remainingCents = amount.inCents() - allocations.sumOf(CompensationAllocation::amountInCents)
    val membersReceivingExtraCent =
        allocations
            .sortedWith(
                compareByDescending<CompensationAllocation> { allocation -> allocation.remainder }
                    .thenBy { allocation -> allocation.member.toPrimitive() },
            ).take(remainingCents.toInt())
            .map(CompensationAllocation::member)
            .toSet()

    return allocations
        .map { allocation ->
            allocation.copy(
                amountInCents = allocation.amountInCents + if (allocation.member in membersReceivingExtraCent) 1 else 0,
            )
        }.filter { allocation -> allocation.amountInCents > 0 }
        .map { allocation ->
            MemberBalanceTransfer(
                fromMember = withdrawnBy,
                toMember = allocation.member,
                amount = MoneyAmount.ofCents(allocation.amountInCents),
            )
        }.toSet()
}

private data class CompensationWeight(
    val member: MemberEmail,
    val amount: Long,
)

private data class CompensationAllocation(
    val member: MemberEmail,
    val amountInCents: Long,
    val remainder: BigInteger,
)
