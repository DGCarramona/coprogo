package tech.justdev.domain.expense.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import tech.justdev.domain.expense.valueobject.ExpenseParticipation
import tech.justdev.domain.expense.valueobject.ExpenseParticipationDecision
import tech.justdev.domain.expense.valueobject.ExpenseParticipationStatus
import tech.justdev.domain.expense.valueobject.ExpenseShare
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.testsupport.expenseId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberId
import java.time.Instant

class ExpenseTest {

    @Test
    fun `proposeEqualSplit should split remainder deterministically by member id`() {
        assertEquals(
            Expense(
                id = expenseId("expense-1"),
                group = groupId("group-1"),
                title = "Boiler repair",
                createdBy = memberId("alice"),
                totalAmount = MoneyAmount.ofCents(100),
                createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                participations = setOf(
                    ExpenseParticipation(memberId("alice"), MoneyAmount.ofCents(34), ExpenseParticipationStatus.Approved(Instant.parse("2026-04-03T10:00:00Z"))),
                    ExpenseParticipation(memberId("bob"), MoneyAmount.ofCents(33), ExpenseParticipationStatus.Pending),
                    ExpenseParticipation(memberId("carol"), MoneyAmount.ofCents(33), ExpenseParticipationStatus.Pending),
                ),
            ),
            Expense.proposeEqualSplit(
                id = expenseId("expense-1"),
                group = groupId("group-1"),
                title = "Boiler repair",
                createdBy = memberId("alice"),
                totalAmount = MoneyAmount.ofCents(100),
                createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                participants = setOf(memberId("carol"), memberId("alice"), memberId("bob")),
            ),
        )
    }

    @Test
    fun `proposeEqualSplit should fail when at least one participant would receive zero`() {
        assertThrows(IllegalArgumentException::class.java) {
            Expense.proposeEqualSplit(
                id = expenseId("expense-1"),
                group = groupId("group-1"),
                title = "Boiler repair",
                createdBy = memberId("alice"),
                totalAmount = MoneyAmount.ofCents(2),
                createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                participants = setOf(memberId("alice"), memberId("bob"), memberId("carol")),
            )
        }
    }

    @Test
    fun `propose should auto approve creator participation and wait for other members`() {
        assertEquals(
            proposedExpense(),
            Expense.propose(
                id = expenseId("expense-1"),
                group = groupId("group-1"),
                title = "Plumber invoice",
                createdBy = memberId("alice"),
                totalAmount = MoneyAmount.ofCents(100),
                createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                shares = setOf(
                    ExpenseShare(memberId("alice"), MoneyAmount.ofCents(40)),
                    ExpenseShare(memberId("bob"), MoneyAmount.ofCents(60)),
                ),
            ),
        )
    }

    @Test
    fun `recordParticipationDecision should accept expense when last pending member approves`() {
        val proposedExpense = proposedExpense()

        assertEquals(
            Expense(
                id = expenseId("expense-1"),
                group = groupId("group-1"),
                title = "Plumber invoice",
                createdBy = memberId("alice"),
                totalAmount = MoneyAmount.ofCents(100),
                createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                participations = setOf(
                    ExpenseParticipation(memberId("alice"), MoneyAmount.ofCents(40), ExpenseParticipationStatus.Approved(Instant.parse("2026-04-03T10:00:00Z"))),
                    ExpenseParticipation(memberId("bob"), MoneyAmount.ofCents(60), ExpenseParticipationStatus.Approved(Instant.parse("2026-04-03T12:00:00Z"))),
                ),
            ),
            proposedExpense.recordParticipationDecision(
                member = memberId("bob"),
                decision = ExpenseParticipationDecision.APPROVE,
                decidedAt = Instant.parse("2026-04-03T12:00:00Z"),
            ),
        )
    }

    @Test
    fun `recordParticipationDecision should invalidate expense when a member refuses`() {
        val proposedExpense = proposedExpense()

        assertEquals(
            Expense(
                id = expenseId("expense-1"),
                group = groupId("group-1"),
                title = "Plumber invoice",
                createdBy = memberId("alice"),
                totalAmount = MoneyAmount.ofCents(100),
                createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                participations = setOf(
                    ExpenseParticipation(memberId("alice"), MoneyAmount.ofCents(40), ExpenseParticipationStatus.Approved(Instant.parse("2026-04-03T10:00:00Z"))),
                    ExpenseParticipation(memberId("bob"), MoneyAmount.ofCents(60), ExpenseParticipationStatus.Refused(Instant.parse("2026-04-03T12:00:00Z"))),
                ),
            ),
            proposedExpense.recordParticipationDecision(
                member = memberId("bob"),
                decision = ExpenseParticipationDecision.REFUSE,
                decidedAt = Instant.parse("2026-04-03T12:00:00Z"),
            ),
        )
    }

    @Test
    fun `propose should fail when creator does not participate`() {
        assertThrows(IllegalArgumentException::class.java) {
            Expense.propose(
                id = expenseId("expense-1"),
                group = groupId("group-1"),
                title = "Plumber invoice",
                createdBy = memberId("alice"),
                totalAmount = MoneyAmount.ofCents(100),
                createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                shares = setOf(
                    ExpenseShare(memberId("bob"), MoneyAmount.ofCents(100)),
                ),
            )
        }
    }

    private fun proposedExpense(): Expense =
        Expense.propose(
            id = expenseId("expense-1"),
            group = groupId("group-1"),
            title = "Plumber invoice",
            createdBy = memberId("alice"),
            totalAmount = MoneyAmount.ofCents(100),
            createdAt = Instant.parse("2026-04-03T10:00:00Z"),
            shares = setOf(
                ExpenseShare(memberId("alice"), MoneyAmount.ofCents(40)),
                ExpenseShare(memberId("bob"), MoneyAmount.ofCents(60)),
            ),
        )
}
