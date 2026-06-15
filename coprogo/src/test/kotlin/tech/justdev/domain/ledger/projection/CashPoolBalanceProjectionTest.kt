package tech.justdev.domain.ledger.projection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tech.justdev.domain.ledger.effect.MemberBalanceTransfer
import tech.justdev.domain.ledger.effect.MemberCashPoolShareDelta
import tech.justdev.domain.ledger.event.CashPoolIncomeLedgerEvent
import tech.justdev.domain.ledger.event.CashPoolWithdrawalLedgerEvent
import tech.justdev.domain.ledger.valueobject.NetBalanceAmount
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.ledgerEventId
import tech.justdev.testsupport.memberEmail
import java.time.Instant

class CashPoolBalanceProjectionTest {
    @Test
    fun `projectCashPoolBalance should aggregate income and withdrawals affecting the common cash pool`() {
        val cashPoolBalance =
            listOf(
                CashPoolIncomeLedgerEvent(
                    id = ledgerEventId("cash-pool-income-1"),
                    group = groupId("group-1"),
                    amount = MoneyAmount.ofCents(100),
                    allocations =
                        setOf(
                            MemberCashPoolShareDelta.increase(
                                member = memberEmail("alice"),
                                amount = MoneyAmount.ofCents(100),
                            ),
                        ),
                    occurredAt = Instant.parse("2026-04-03T10:00:00Z"),
                ),
                CashPoolWithdrawalLedgerEvent(
                    id = ledgerEventId("cash-pool-withdrawal-1"),
                    group = groupId("group-1"),
                    withdrawnBy = memberEmail("alice"),
                    withdrawnAmount = MoneyAmount.ofCents(35),
                    ownRevenueShareConsumed = MoneyAmount.ofCents(25),
                    balanceTransfers =
                        setOf(
                            MemberBalanceTransfer(
                                fromMember = memberEmail("alice"),
                                toMember = memberEmail("bob"),
                                amount = MoneyAmount.ofCents(10),
                            ),
                        ),
                    occurredAt = Instant.parse("2026-04-03T12:00:00Z"),
                ),
            ).projectCashPoolBalance()

        assertEquals(NetBalanceAmount.ofCents(65), cashPoolBalance)
    }
}
