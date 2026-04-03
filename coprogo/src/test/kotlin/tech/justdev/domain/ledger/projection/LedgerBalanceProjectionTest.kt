package tech.justdev.domain.ledger.projection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tech.justdev.domain.ledger.event.AcceptedExpenseLedgerEvent
import tech.justdev.domain.ledger.event.LedgerTransfer
import tech.justdev.domain.ledger.valueobject.NetBalanceAmount
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.testsupport.acceptedExpenseLedgerEventId
import tech.justdev.testsupport.expenseId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberId
import java.time.Instant

class LedgerBalanceProjectionTest {

    @Test
    fun `projectMemberBalances should aggregate incoming and outgoing transfers into net balances`() {
        val balances = listOf(
            AcceptedExpenseLedgerEvent(
                id = acceptedExpenseLedgerEventId("expense-1"),
                group = groupId("group-1"),
                expense = expenseId("expense-1"),
                paidBy = memberId("alice"),
                occurredAt = Instant.parse("2026-04-03T10:00:00Z"),
                transfers = setOf(
                    LedgerTransfer(memberId("bob"), memberId("alice"), MoneyAmount.ofCents(30)),
                ),
            ),
            AcceptedExpenseLedgerEvent(
                id = acceptedExpenseLedgerEventId("expense-2"),
                group = groupId("group-1"),
                expense = expenseId("expense-2"),
                paidBy = memberId("bob"),
                occurredAt = Instant.parse("2026-04-03T11:00:00Z"),
                transfers = setOf(
                    LedgerTransfer(memberId("alice"), memberId("bob"), MoneyAmount.ofCents(10)),
                ),
            ),
        ).projectMemberBalances()

        assertEquals(
            setOf(
                MemberLedgerBalance(memberId("alice"), NetBalanceAmount.ofCents(20)),
                MemberLedgerBalance(memberId("bob"), NetBalanceAmount.ofCents(-20)),
            ),
            balances,
        )
    }
}
