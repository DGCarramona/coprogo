package tech.justdev.domain.ledger.projection

import tech.justdev.domain.ledger.effect.MemberBalanceTransfer
import tech.justdev.domain.ledger.event.LedgerEvent
import tech.justdev.domain.ledger.valueobject.NetBalanceAmount
import tech.justdev.domain.shared.valueobject.MemberId

data class MemberLedgerBalance(
    val member: MemberId,
    val netAmount: NetBalanceAmount,
)

fun Iterable<LedgerEvent>.projectMemberBalances(): Set<MemberLedgerBalance> {
    val balancesByMember = fold(mutableMapOf<MemberId, NetBalanceAmount>()) { balances, event ->
        event.effects
            .filterIsInstance<MemberBalanceTransfer>()
            .fold(balances) { currentBalances, transfer ->
                currentBalances.also {
                    it.accumulate(transfer.fromMember, NetBalanceAmount.debt(transfer.amount))
                    it.accumulate(transfer.toMember, NetBalanceAmount.credit(transfer.amount))
                }
            }
    }

    return balancesByMember.entries
        .mapNotNull { (member, netAmount) ->
            netAmount
                .takeUnless(NetBalanceAmount::isZero)
                ?.let { MemberLedgerBalance(member = member, netAmount = it) }
        }
        .toSet()
}

private fun MutableMap<MemberId, NetBalanceAmount>.accumulate(member: MemberId, delta: NetBalanceAmount) {
    val currentAmount = getOrElse(member) { NetBalanceAmount.ZERO }
    put(member, currentAmount + delta)
}
