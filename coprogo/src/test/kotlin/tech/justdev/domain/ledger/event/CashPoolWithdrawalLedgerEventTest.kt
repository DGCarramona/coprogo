package tech.justdev.domain.ledger.event

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tech.justdev.domain.ledger.effect.CashPoolBalanceDelta
import tech.justdev.domain.ledger.effect.MemberBalanceTransfer
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.ledgerEventId
import tech.justdev.testsupport.memberEmail
import java.time.Instant

class CashPoolWithdrawalLedgerEventTest {
    @Test
    fun `effects should omit a member share delta when no own revenue share is consumed`() {
        val compensation =
            MemberBalanceTransfer(
                fromMember = memberEmail("alice"),
                toMember = memberEmail("bob"),
                amount = MoneyAmount.ofCents(50),
            )
        val event =
            CashPoolWithdrawalLedgerEvent(
                id = ledgerEventId("withdrawal-without-own-share"),
                group = groupId("group-1"),
                withdrawnBy = memberEmail("alice"),
                withdrawnAmount = MoneyAmount.ofCents(50),
                ownRevenueShareConsumed = MoneyAmount.ZERO,
                balanceTransfers = setOf(compensation),
                occurredAt = Instant.parse("2026-04-03T12:00:00Z"),
            )

        assertEquals(
            setOf(CashPoolBalanceDelta.decrease(MoneyAmount.ofCents(50)), compensation),
            event.effects,
        )
    }
}
