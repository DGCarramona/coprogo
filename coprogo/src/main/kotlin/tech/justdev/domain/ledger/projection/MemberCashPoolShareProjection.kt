package tech.justdev.domain.ledger.projection

import tech.justdev.domain.ledger.effect.MemberCashPoolShareDelta
import tech.justdev.domain.ledger.event.LedgerEvent
import tech.justdev.domain.ledger.valueobject.NetBalanceAmount
import tech.justdev.domain.shared.valueobject.MemberId

data class MemberCashPoolShareBalance(
    val memberId: MemberId,
    val amount: NetBalanceAmount,
)

fun Iterable<LedgerEvent>.projectMemberCashPoolShares(): Set<MemberCashPoolShareBalance> {
    val amountsByMember = fold(mutableMapOf<MemberId, NetBalanceAmount>()) { amounts, event ->
        event.effects
            .filterIsInstance<MemberCashPoolShareDelta>()
            .fold(amounts) { currentAmounts, delta ->
                currentAmounts.also {
                    val currentAmount = it.getOrElse(delta.member) { NetBalanceAmount.ZERO }
                    it[delta.member] = currentAmount + delta.amount
                }
            }
    }

    return amountsByMember.entries
        .mapNotNull { (memberId, amount) ->
            amount
                .takeUnless(NetBalanceAmount::isZero)
                ?.let { MemberCashPoolShareBalance(memberId = memberId, amount = it) }
        }
        .toSet()
}
