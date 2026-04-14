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
import tech.justdev.testsupport.memberEmail
import java.time.Instant

class ExpenseTest {
    @Test
    fun `proposeEqualSplit should split remainder deterministically by member id`() {
        assertEquals(
            Expense(
                id = expenseId("expense-1"),
                group = groupId("group-1"),
                title = "Boiler repair",
                createdBy = memberEmail("alice"),
                totalAmount = MoneyAmount.ofCents(100),
                createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                participations =
                    setOf(
                        ExpenseParticipation(
                            memberEmail("alice"),
                            MoneyAmount.ofCents(34),
                            ExpenseParticipationStatus.Approved(Instant.parse("2026-04-03T10:00:00Z")),
                        ),
                        ExpenseParticipation(memberEmail("bob"), MoneyAmount.ofCents(33), ExpenseParticipationStatus.Pending),
                        ExpenseParticipation(memberEmail("carol"), MoneyAmount.ofCents(33), ExpenseParticipationStatus.Pending),
                    ),
            ),
            Expense.proposeEqualSplit(
                id = expenseId("expense-1"),
                group = groupId("group-1"),
                title = "Boiler repair",
                createdBy = memberEmail("alice"),
                totalAmount = MoneyAmount.ofCents(100),
                createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                participants = setOf(memberEmail("carol"), memberEmail("alice"), memberEmail("bob")),
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
                createdBy = memberEmail("alice"),
                totalAmount = MoneyAmount.ofCents(2),
                createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                participants = setOf(memberEmail("alice"), memberEmail("bob"), memberEmail("carol")),
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
                createdBy = memberEmail("alice"),
                totalAmount = MoneyAmount.ofCents(100),
                createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                shares =
                    setOf(
                        ExpenseShare(memberEmail("alice"), MoneyAmount.ofCents(40)),
                        ExpenseShare(memberEmail("bob"), MoneyAmount.ofCents(60)),
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
                createdBy = memberEmail("alice"),
                totalAmount = MoneyAmount.ofCents(100),
                createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                participations =
                    setOf(
                        ExpenseParticipation(
                            memberEmail("alice"),
                            MoneyAmount.ofCents(40),
                            ExpenseParticipationStatus.Approved(Instant.parse("2026-04-03T10:00:00Z")),
                        ),
                        ExpenseParticipation(
                            memberEmail("bob"),
                            MoneyAmount.ofCents(60),
                            ExpenseParticipationStatus.Approved(Instant.parse("2026-04-03T12:00:00Z")),
                        ),
                    ),
            ),
            proposedExpense.recordParticipationDecision(
                member = memberEmail("bob"),
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
                createdBy = memberEmail("alice"),
                totalAmount = MoneyAmount.ofCents(100),
                createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                participations =
                    setOf(
                        ExpenseParticipation(
                            memberEmail("alice"),
                            MoneyAmount.ofCents(40),
                            ExpenseParticipationStatus.Approved(Instant.parse("2026-04-03T10:00:00Z")),
                        ),
                        ExpenseParticipation(
                            memberEmail("bob"),
                            MoneyAmount.ofCents(60),
                            ExpenseParticipationStatus.Refused(Instant.parse("2026-04-03T12:00:00Z")),
                        ),
                    ),
            ),
            proposedExpense.recordParticipationDecision(
                member = memberEmail("bob"),
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
                createdBy = memberEmail("alice"),
                totalAmount = MoneyAmount.ofCents(100),
                createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                shares =
                    setOf(
                        ExpenseShare(memberEmail("bob"), MoneyAmount.ofCents(100)),
                    ),
            )
        }
    }

    private fun proposedExpense(): Expense =
        Expense.propose(
            id = expenseId("expense-1"),
            group = groupId("group-1"),
            title = "Plumber invoice",
            createdBy = memberEmail("alice"),
            totalAmount = MoneyAmount.ofCents(100),
            createdAt = Instant.parse("2026-04-03T10:00:00Z"),
            shares =
                setOf(
                    ExpenseShare(memberEmail("alice"), MoneyAmount.ofCents(40)),
                    ExpenseShare(memberEmail("bob"), MoneyAmount.ofCents(60)),
                ),
        )
}
