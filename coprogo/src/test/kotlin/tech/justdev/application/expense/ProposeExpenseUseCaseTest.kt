package tech.justdev.application.expense

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tech.justdev.application.support.InMemoryExpenseRepository
import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.valueobject.ExpenseParticipation
import tech.justdev.domain.expense.valueobject.ExpenseParticipationStatus
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.testsupport.FixedExpenseIdGenerator
import tech.justdev.testsupport.expenseId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import java.time.Instant

class ProposeExpenseUseCaseTest {
    @Test
    fun `invoke should create and persist a proposed expense with equal split participations`() {
        runTest {
            val expenseRepository = InMemoryExpenseRepository()
            val useCase =
                ProposeExpenseUseCase(
                    expenseRepository = expenseRepository,
                    expenseIdGenerator = FixedExpenseIdGenerator(listOf(expenseId("expense-1"))),
                )

            useCase(
                ProposeExpenseCommand(
                    group = groupId("group-1"),
                    title = "Boiler repair",
                    createdBy = memberEmail("alice"),
                    totalAmountInCents = 100,
                    createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                    allocation =
                        EqualSplitExpenseAllocationCommand(
                            participants =
                                setOf(
                                    memberEmail("alice"),
                                    memberEmail("bob"),
                                    memberEmail("carol"),
                                ),
                        ),
                ),
            )

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
                            savedExpenseParticipation(
                                "alice",
                                34,
                                ExpenseParticipationStatus.Approved(
                                    Instant.parse("2026-04-03T10:00:00Z"),
                                ),
                            ),
                            savedExpenseParticipation("bob", 33, ExpenseParticipationStatus.Pending),
                            savedExpenseParticipation("carol", 33, ExpenseParticipationStatus.Pending),
                        ),
                ),
                expenseRepository.findById(expenseId("expense-1")),
            )
        }
    }

    @Test
    fun `invoke should accept immediately when creator is the only participant`() {
        runTest {
            val expenseRepository = InMemoryExpenseRepository()
            val useCase =
                ProposeExpenseUseCase(
                    expenseRepository = expenseRepository,
                    expenseIdGenerator = FixedExpenseIdGenerator(listOf(expenseId("expense-2"))),
                )

            useCase(
                ProposeExpenseCommand(
                    group = groupId("group-1"),
                    title = "Private purchase",
                    createdBy = memberEmail("alice"),
                    totalAmountInCents = 100,
                    createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                    allocation =
                        FixedExpenseAllocationCommand(
                            participations =
                                setOf(
                                    FixedExpenseParticipationCommand(memberEmail("alice"), 100),
                                ),
                        ),
                ),
            )

            assertEquals(
                Expense(
                    id = expenseId("expense-2"),
                    group = groupId("group-1"),
                    title = "Private purchase",
                    createdBy = memberEmail("alice"),
                    totalAmount = MoneyAmount.ofCents(100),
                    createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                    participations =
                        setOf(
                            savedExpenseParticipation(
                                "alice",
                                100,
                                ExpenseParticipationStatus.Approved(
                                    Instant.parse("2026-04-03T10:00:00Z"),
                                ),
                            ),
                        ),
                ),
                expenseRepository.findById(expenseId("expense-2")),
            )
        }
    }

    private fun savedExpenseParticipation(
        memberId: String,
        amountInCents: Long,
        status: ExpenseParticipationStatus,
    ) = ExpenseParticipation(
        member = memberEmail(memberId),
        amount = MoneyAmount.ofCents(amountInCents),
        status = status,
    )
}
