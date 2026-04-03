package tech.justdev.application.expense

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import tech.justdev.application.support.InMemoryExpenseRepository
import tech.justdev.application.support.InMemoryLedgerEventRepository
import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.valueobject.ExpenseParticipation
import tech.justdev.domain.expense.valueobject.ExpenseParticipationStatus
import tech.justdev.domain.expense.valueobject.ExpenseShare
import tech.justdev.domain.ledger.effect.MemberBalanceTransfer
import tech.justdev.domain.ledger.event.AcceptedExpenseLedgerEvent
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.testsupport.acceptedExpenseLedgerEventId
import tech.justdev.testsupport.expenseId
import tech.justdev.testsupport.expenseUuid
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberId
import tech.justdev.testsupport.memberUuid
import java.time.Instant

class RecordExpenseParticipationDecisionUseCaseTest {

    @Test
    fun `invoke should append a ledger event when the last pending member approves`() {
        val expenseRepository = InMemoryExpenseRepository(
            expenses = listOf(proposedExpense()),
        )
        val ledgerEventRepository = InMemoryLedgerEventRepository()
        val useCase = RecordExpenseParticipationDecisionUseCase(
            expenseRepository = expenseRepository,
            ledgerEventRepository = ledgerEventRepository,
        )

        useCase(
            RecordExpenseParticipationDecisionCommand(
                id = expenseUuid("expense-1"),
                member = memberUuid("bob"),
                decision = ExpenseParticipationDecisionCommand.APPROVE,
                decidedAt = Instant.parse("2026-04-03T12:00:00Z"),
            ),
        )

        assertEquals(
            Expense(
                id = expenseId("expense-1"),
                group = groupId("group-1"),
                title = "Boiler repair",
                createdBy = memberId("alice"),
                totalAmount = MoneyAmount.ofCents(100),
                createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                participations = setOf(
                    ExpenseParticipation(
                        member = memberId("alice"),
                        amount = MoneyAmount.ofCents(40),
                        status = ExpenseParticipationStatus.Approved(Instant.parse("2026-04-03T10:00:00Z")),
                    ),
                    ExpenseParticipation(
                        member = memberId("bob"),
                        amount = MoneyAmount.ofCents(60),
                        status = ExpenseParticipationStatus.Approved(Instant.parse("2026-04-03T12:00:00Z")),
                    ),
                ),
            ),
            expenseRepository.findById(expenseId("expense-1")),
        )
        assertEquals(
            listOf(
                AcceptedExpenseLedgerEvent(
                    id = acceptedExpenseLedgerEventId("expense-1"),
                    group = groupId("group-1"),
                    expense = expenseId("expense-1"),
                    paidBy = memberId("alice"),
                    occurredAt = Instant.parse("2026-04-03T12:00:00Z"),
                    transfers = setOf(
                        MemberBalanceTransfer(
                            fromMember = memberId("bob"),
                            toMember = memberId("alice"),
                            amount = MoneyAmount.ofCents(60),
                        ),
                    ),
                ),
            ),
            ledgerEventRepository.allEvents(),
        )
    }

    @Test
    fun `invoke should invalidate expense without appending a ledger event when a member refuses`() {
        val expenseRepository = InMemoryExpenseRepository(
            expenses = listOf(proposedExpense()),
        )
        val ledgerEventRepository = InMemoryLedgerEventRepository()
        val useCase = RecordExpenseParticipationDecisionUseCase(
            expenseRepository = expenseRepository,
            ledgerEventRepository = ledgerEventRepository,
        )

        useCase(
            RecordExpenseParticipationDecisionCommand(
                id = expenseUuid("expense-1"),
                member = memberUuid("bob"),
                decision = ExpenseParticipationDecisionCommand.REFUSE,
                decidedAt = Instant.parse("2026-04-03T12:00:00Z"),
            ),
        )

        assertEquals(
            Expense(
                id = expenseId("expense-1"),
                group = groupId("group-1"),
                title = "Boiler repair",
                createdBy = memberId("alice"),
                totalAmount = MoneyAmount.ofCents(100),
                createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                participations = setOf(
                    ExpenseParticipation(
                        member = memberId("alice"),
                        amount = MoneyAmount.ofCents(40),
                        status = ExpenseParticipationStatus.Approved(Instant.parse("2026-04-03T10:00:00Z")),
                    ),
                    ExpenseParticipation(
                        member = memberId("bob"),
                        amount = MoneyAmount.ofCents(60),
                        status = ExpenseParticipationStatus.Refused(Instant.parse("2026-04-03T12:00:00Z")),
                    ),
                ),
            ),
            expenseRepository.findById(expenseId("expense-1")),
        )
        assertEquals(emptyList<AcceptedExpenseLedgerEvent>(), ledgerEventRepository.allEvents())
    }

    @Test
    fun `invoke should fail when the expense is already accepted because only proposed expenses can receive decisions`() {
        val acceptedExpense = proposedExpense().recordParticipationDecision(
            member = memberId("bob"),
            decision = tech.justdev.domain.expense.valueobject.ExpenseParticipationDecision.APPROVE,
            decidedAt = Instant.parse("2026-04-03T12:00:00Z"),
        )
        val expenseRepository = InMemoryExpenseRepository(
            expenses = listOf(acceptedExpense),
        )
        val ledgerEventRepository = InMemoryLedgerEventRepository()
        val useCase = RecordExpenseParticipationDecisionUseCase(
            expenseRepository = expenseRepository,
            ledgerEventRepository = ledgerEventRepository,
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            useCase(
                RecordExpenseParticipationDecisionCommand(
                    id = expenseUuid("expense-1"),
                    member = memberUuid("bob"),
                    decision = ExpenseParticipationDecisionCommand.APPROVE,
                    decidedAt = Instant.parse("2026-04-03T13:00:00Z"),
                ),
            )
        }

        assertEquals("expense ${expenseUuid("expense-1")} was not found", error.message)
        assertEquals(acceptedExpense, expenseRepository.findById(expenseId("expense-1")))
        assertEquals(emptyList<AcceptedExpenseLedgerEvent>(), ledgerEventRepository.allEvents())
    }

    private fun proposedExpense(): Expense =
        Expense.propose(
            id = expenseId("expense-1"),
            group = groupId("group-1"),
            title = "Boiler repair",
            createdBy = memberId("alice"),
            totalAmount = MoneyAmount.ofCents(100),
            createdAt = Instant.parse("2026-04-03T10:00:00Z"),
            shares = setOf(
                ExpenseShare(memberId("alice"), MoneyAmount.ofCents(40)),
                ExpenseShare(memberId("bob"), MoneyAmount.ofCents(60)),
            ),
        )
}
