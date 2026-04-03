package tech.justdev.domain.ledger.projection

import tech.justdev.domain.ledger.valueobject.NetBalanceAmount
import tech.justdev.domain.ledger.event.LedgerEvent
import tech.justdev.domain.shared.valueobject.MemberId

data class MemberLedgerBalance(
    val memberId: MemberId,
    val netAmount: NetBalanceAmount,
)

fun Iterable<LedgerEvent>.projectMemberBalances(): Set<MemberLedgerBalance> {
    val balancesByMember = fold(mutableMapOf<MemberId, NetBalanceAmount>()) { balances, event ->
        event.transfers.fold(balances) { currentBalances, transfer ->
            currentBalances.also {
                it.accumulate(transfer.fromMember, NetBalanceAmount.debt(transfer.amount))
                it.accumulate(transfer.toMember, NetBalanceAmount.credit(transfer.amount))
            }
        }
    }

    return balancesByMember.entries
        .mapNotNull { (memberId, netAmount) ->
            netAmount
                .takeUnless(NetBalanceAmount::isZero)
                ?.let { MemberLedgerBalance(memberId = memberId, netAmount = it) }
        }
        .toSet()
}

private fun MutableMap<MemberId, NetBalanceAmount>.accumulate(memberId: MemberId, delta: NetBalanceAmount) {
    val currentAmount = getOrElse(memberId) { NetBalanceAmount.ZERO }
    put(memberId, currentAmount + delta)
}
