package tech.justdev.domain.ledger.event

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.valueobject.ExpenseParticipationDecision
import tech.justdev.domain.expense.valueobject.ExpenseShare
import tech.justdev.domain.ledger.effect.MemberBalanceTransfer
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.testsupport.acceptedExpenseLedgerEventId
import tech.justdev.testsupport.expenseId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberId
import java.time.Instant

class AcceptedExpenseLedgerEventTest {

    @Test
    fun `from should build transfers from other participants to the creator`() {
        val acceptedExpense = Expense.propose(
            id = expenseId("expense-1"),
            group = groupId("group-1"),
            title = "Boiler repair",
            createdBy = memberId("alice"),
            totalAmount = MoneyAmount.ofCents(100),
            createdAt = Instant.parse("2026-04-03T10:00:00Z"),
            shares = setOf(
                ExpenseShare(memberId("alice"), MoneyAmount.ofCents(40)),
                ExpenseShare(memberId("bob"), MoneyAmount.ofCents(35)),
                ExpenseShare(memberId("carol"), MoneyAmount.ofCents(25)),
            ),
        ).recordParticipationDecision(
            member = memberId("bob"),
            decision = ExpenseParticipationDecision.APPROVE,
            decidedAt = Instant.parse("2026-04-03T11:00:00Z"),
        ).recordParticipationDecision(
            member = memberId("carol"),
            decision = ExpenseParticipationDecision.APPROVE,
            decidedAt = Instant.parse("2026-04-03T12:00:00Z"),
        )

        assertEquals(
            AcceptedExpenseLedgerEvent(
                id = acceptedExpenseLedgerEventId("expense-1"),
                group = groupId("group-1"),
                expense = expenseId("expense-1"),
                paidBy = memberId("alice"),
                occurredAt = Instant.parse("2026-04-03T12:00:00Z"),
                transfers = setOf(
                    MemberBalanceTransfer(memberId("bob"), memberId("alice"), MoneyAmount.ofCents(35)),
                    MemberBalanceTransfer(memberId("carol"), memberId("alice"), MoneyAmount.ofCents(25)),
                ),
            ),
            AcceptedExpenseLedgerEvent.from(acceptedExpense),
        )
    }

    @Test
    fun `from should skip self only expenses because they do not affect balances`() {
        val selfOnlyExpense = Expense.propose(
            id = expenseId("expense-1"),
            group = groupId("group-1"),
            title = "Private purchase",
            createdBy = memberId("alice"),
            totalAmount = MoneyAmount.ofCents(100),
            createdAt = Instant.parse("2026-04-03T10:00:00Z"),
            shares = setOf(
                ExpenseShare(memberId("alice"), MoneyAmount.ofCents(100)),
            ),
        )

        assertNull(AcceptedExpenseLedgerEvent.from(selfOnlyExpense))
    }
}
