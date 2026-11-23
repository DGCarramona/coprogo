package tech.justdev.domain.ledger.projection

import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.ledger.effect.MemberBalanceTransfer
import tech.justdev.domain.ledger.event.CashPoolWithdrawalLedgerEvent
import tech.justdev.domain.ledger.event.LedgerEvent
import tech.justdev.domain.ledger.valueobject.NetBalanceAmount
import tech.justdev.domain.shared.money.MoneyAmount

data class MemberBalanceImpact(
    val event: LedgerEvent,
    val amount: MoneyAmount,
    val effect: MemberBalanceImpactType = MemberBalanceImpactType.DEBT_REDUCTION,
)

enum class MemberBalanceImpactType {
    DEBT_REDUCTION,
    DEBT_INCREASE,
    CREDIT_REDUCTION,
}

fun Iterable<LedgerEvent>.projectMemberBalanceImpacts(member: MemberEmail): List<MemberBalanceImpact> =
    sortedWith(
        compareBy<LedgerEvent> { event -> event.occurredAt }
            .thenBy { event -> event.id.toPrimitive() },
    ).runningFold(MemberBalanceImpactProjectionState()) { state, event ->
        val transfers = event.effects.filterIsInstance<MemberBalanceTransfer>()
        val incomingAmountInCents =
            transfers
                .asSequence()
                .filter { transfer -> transfer.toMember == member }
                .sumOf { transfer -> transfer.amount.inCents() }
        val outgoingAmountInCents =
            transfers
                .asSequence()
                .filter { transfer -> transfer.fromMember == member }
                .sumOf { transfer -> transfer.amount.inCents() }
        val debtBeforeEventInCents = (-state.balance.inCents()).coerceAtLeast(0)
        val reducedAmountInCents = minOf(debtBeforeEventInCents, incomingAmountInCents)
        val creditReducedInCents =
            if (event is CashPoolWithdrawalLedgerEvent) {
                minOf(state.balance.inCents().coerceAtLeast(0), outgoingAmountInCents)
            } else {
                0L
            }
        val debtIncreasedInCents =
            if (event is CashPoolWithdrawalLedgerEvent) outgoingAmountInCents - creditReducedInCents else 0L
        val updatedBalance =
            state.balance + NetBalanceAmount.ofCents(incomingAmountInCents - outgoingAmountInCents)

        MemberBalanceImpactProjectionState(
            balance = updatedBalance,
            impacts =
                listOf(
                    MemberBalanceImpactType.DEBT_REDUCTION to reducedAmountInCents,
                    MemberBalanceImpactType.CREDIT_REDUCTION to creditReducedInCents,
                    MemberBalanceImpactType.DEBT_INCREASE to debtIncreasedInCents,
                ).mapNotNull { (effect, amount) ->
                    amount.takeIf { it > 0 }?.let { MemberBalanceImpact(event, MoneyAmount.ofCents(it), effect) }
                },
        )
    }.flatMap(MemberBalanceImpactProjectionState::impacts)

private data class MemberBalanceImpactProjectionState(
    val balance: NetBalanceAmount = NetBalanceAmount.ZERO,
    val impacts: List<MemberBalanceImpact> = emptyList(),
)
