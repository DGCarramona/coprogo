package tech.justdev.domain.ledger.projection

import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.ledger.effect.CashPoolBalanceDelta
import tech.justdev.domain.ledger.effect.MemberCashPoolShareDelta
import tech.justdev.domain.ledger.event.CashPoolIncomeLedgerEvent
import tech.justdev.domain.ledger.event.CashPoolWithdrawalLedgerEvent
import tech.justdev.domain.ledger.event.LedgerEvent
import tech.justdev.domain.ledger.valueobject.LedgerEventId
import tech.justdev.domain.ledger.valueobject.NetBalanceAmount
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import java.time.ZoneOffset

fun List<LedgerEvent>.replayCashPoolChronologically(): List<LedgerEvent> {
    val states = mutableMapOf<GroupId, CashPoolReplayState>()
    return sortedWith(cashPoolChronologicalOrder).map { event ->
        val state = states.getOrPut(event.group) { CashPoolReplayState() }
        val replayed =
            if (event is CashPoolWithdrawalLedgerEvent) {
                if (state.balance.inCents() < event.withdrawnAmount.inCents()) {
                    throw CashPoolChronologyInsufficientBalance(
                        event.id,
                        event.withdrawnAmount,
                        MoneyAmount.ofCents(state.balance.inCents().coerceAtLeast(0)),
                    )
                }
                CashPoolWithdrawalLedgerEvent.from(
                    id = event.id,
                    group = event.group,
                    withdrawnBy = event.withdrawnBy,
                    withdrawnAmount = event.withdrawnAmount,
                    occurredAt = event.occurredAt,
                    memberCashPoolShares =
                        state.shares
                            .map { (member, amount) ->
                                MemberCashPoolShareBalance(member = member, amount = amount)
                            }.toSet(),
                )
            } else {
                event
            }
        state.apply(replayed)
        replayed
    }
}

fun cashPoolWithdrawalCapacity(
    events: List<LedgerEvent>,
    candidate: CashPoolWithdrawalLedgerEvent,
): MoneyAmount {
    var balance = 0L
    var minimum: Long? = null
    (events + candidate).sortedWith(cashPoolChronologicalOrder).forEach { event ->
        if (event.id == candidate.id) {
            minimum = balance
        } else {
            event.effects.filterIsInstance<CashPoolBalanceDelta>().forEach { delta ->
                balance += delta.amount.inCents()
            }
            minimum = minimum?.let { minOf(it, balance) }
        }
    }
    return MoneyAmount.ofCents(checkNotNull(minimum).coerceAtLeast(0))
}

private val cashPoolChronologicalOrder =
    compareBy<LedgerEvent> { it.occurredAt.atOffset(ZoneOffset.UTC).toLocalDate() }
        .thenBy { if (it is CashPoolIncomeLedgerEvent) 0 else 1 }
        .thenBy { it.occurredAt }
        .thenBy { it.id.toPrimitive() }

class CashPoolChronologyInsufficientBalance(
    withdrawal: LedgerEventId,
    requested: MoneyAmount,
    available: MoneyAmount,
) : IllegalArgumentException(
        "Insufficient cash pool balance for withdrawal ${withdrawal.toPrimitive()}: " +
            "requested ${requested.inCents()} cents, available ${available.inCents()} cents",
    )

private class CashPoolReplayState {
    var balance: NetBalanceAmount = NetBalanceAmount.ZERO
        private set
    val shares = mutableMapOf<MemberEmail, NetBalanceAmount>()

    fun apply(event: LedgerEvent) {
        event.effects.forEach { effect ->
            when (effect) {
                is CashPoolBalanceDelta -> balance += effect.amount
                is MemberCashPoolShareDelta -> {
                    shares[effect.member] = (shares[effect.member] ?: NetBalanceAmount.ZERO) + effect.amount
                }
                else -> Unit
            }
        }
    }
}
