package tech.justdev.domain.ledger.projection

import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.ledger.effect.MemberCashPoolShareDelta
import tech.justdev.domain.ledger.event.LedgerEvent
import tech.justdev.domain.ledger.valueobject.NetBalanceAmount

data class MemberCashPoolShareBalance(
    val member: MemberEmail,
    val amount: NetBalanceAmount,
)

fun Iterable<LedgerEvent>.projectMemberCashPoolShares(): Set<MemberCashPoolShareBalance> {
    val amountsByMember =
        fold(mutableMapOf<MemberEmail, NetBalanceAmount>()) { amounts, event ->
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
        .mapNotNull { (member, amount) ->
            amount
                .takeUnless(NetBalanceAmount::isZero)
                ?.let { MemberCashPoolShareBalance(member = member, amount = it) }
        }.toSet()
}
