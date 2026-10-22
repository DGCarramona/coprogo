package tech.justdev.domain.ledger.projection

import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.ledger.effect.MemberBalanceTransfer
import tech.justdev.domain.ledger.event.LedgerEvent
import tech.justdev.domain.ledger.valueobject.NetBalanceAmount
import tech.justdev.domain.shared.money.MoneyAmount

data class MemberDebtReduction(
    val event: LedgerEvent,
    val amount: MoneyAmount,
)

fun Iterable<LedgerEvent>.projectMemberDebtReductions(member: MemberEmail): List<MemberDebtReduction> =
    sortedWith(
        compareBy<LedgerEvent> { event -> event.occurredAt }
            .thenBy { event -> event.id.toPrimitive() },
    ).runningFold(MemberDebtReductionProjectionState()) { state, event ->
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
        val updatedBalance =
            state.balance + NetBalanceAmount.ofCents(incomingAmountInCents - outgoingAmountInCents)

        MemberDebtReductionProjectionState(
            balance = updatedBalance,
            reduction =
                reducedAmountInCents
                    .takeUnless { amount -> amount == 0L }
                    ?.let { amount -> MemberDebtReduction(event, MoneyAmount.ofCents(amount)) },
        )
    }.mapNotNull(MemberDebtReductionProjectionState::reduction)

private data class MemberDebtReductionProjectionState(
    val balance: NetBalanceAmount = NetBalanceAmount.ZERO,
    val reduction: MemberDebtReduction? = null,
)
