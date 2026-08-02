package tech.justdev.application.expense

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.application.group.GroupAccessDeniedException
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.application.support.InMemoryExpenseRepository
import tech.justdev.application.support.InMemoryGroupRepository
import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.valueobject.ExpenseParticipation
import tech.justdev.domain.expense.valueobject.ExpenseParticipationStatus
import tech.justdev.domain.group.entity.Group
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
                useCase(
                    expenseRepository,
                    FixedExpenseIdGenerator(listOf(expenseId("expense-1"))),
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
                expenseRepository.findByIdAndGroup(expenseId("expense-1"), groupId("group-1")),
            )
        }
    }

    @Test
    fun `invoke should accept immediately when creator is the only participant`() {
        runTest {
            val expenseRepository = InMemoryExpenseRepository()
            val useCase =
                useCase(
                    expenseRepository,
                    FixedExpenseIdGenerator(listOf(expenseId("expense-2"))),
                )

            useCase(
                ProposeExpenseCommand(
                    group = groupId("group-1"),
                    title = "Private purchase",
                    createdBy = memberEmail("alice"),
                    totalAmountInCents = 100,
                    createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                    allocation =
                        CustomExpenseAllocationCommand(
                            participations =
                                setOf(
                                    CustomExpenseParticipationCommand(memberEmail("alice"), 100),
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
                expenseRepository.findByIdAndGroup(expenseId("expense-2"), groupId("group-1")),
            )
        }
    }

    @Test
    fun `invoke should reject a creator who is not a group member before generating an expense id`() {
        val expenseRepository = InMemoryExpenseRepository()
        val useCase =
            useCase(
                expenseRepository,
                ExpenseIdGenerator { throw AssertionError("expense id should not be generated") },
            )

        assertThrows<GroupAccessDeniedException> {
            runTest {
                useCase(
                    ProposeExpenseCommand(
                        group = groupId("group-1"),
                        title = "Unauthorized expense",
                        createdBy = memberEmail("outsider"),
                        totalAmountInCents = 100,
                        createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                        allocation = EqualSplitExpenseAllocationCommand(setOf(memberEmail("outsider"))),
                    ),
                )
            }
        }

        runTest {
            assertEquals(null, expenseRepository.findByIdAndGroup(expenseId("expense-1"), groupId("group-1")))
        }
    }

    @Test
    fun `invoke should reject an equal split participant who is not a group member before generating an expense id`() {
        assertNonMemberParticipantIsRejected(
            EqualSplitExpenseAllocationCommand(
                setOf(memberEmail("alice"), memberEmail("outsider")),
            ),
        )
    }

    @Test
    fun `invoke should reject a custom participant who is not a group member before generating an expense id`() {
        assertNonMemberParticipantIsRejected(
            CustomExpenseAllocationCommand(
                setOf(
                    CustomExpenseParticipationCommand(memberEmail("alice"), 50),
                    CustomExpenseParticipationCommand(memberEmail("outsider"), 50),
                ),
            ),
        )
    }

    private fun assertNonMemberParticipantIsRejected(allocation: ExpenseAllocationCommand) {
        val expenseRepository = InMemoryExpenseRepository()
        val useCase =
            useCase(
                expenseRepository,
                ExpenseIdGenerator { throw AssertionError("expense id should not be generated") },
            )

        val error =
            assertThrows<IllegalArgumentException> {
                runTest {
                    useCase(
                        ProposeExpenseCommand(
                            group = groupId("group-1"),
                            title = "Unauthorized expense",
                            createdBy = memberEmail("alice"),
                            totalAmountInCents = 100,
                            createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                            allocation = allocation,
                        ),
                    )
                }
            }

        assertEquals(
            "expense participant ${memberEmail("outsider").toPrimitive()} is not part of group ${groupId("group-1").toPrimitive()}",
            error.message,
        )
        runTest {
            assertEquals(null, expenseRepository.findByIdAndGroup(expenseId("expense-1"), groupId("group-1")))
        }
    }

    private fun useCase(
        expenseRepository: InMemoryExpenseRepository,
        expenseIdGenerator: ExpenseIdGenerator,
    ): ProposeExpenseUseCase =
        ProposeExpenseUseCaseImpl(
            expenseRepository = expenseRepository,
            groupAccessPolicy = GroupAccessPolicy(InMemoryGroupRepository(listOf(group()))),
            expenseIdGenerator = expenseIdGenerator,
        )

    private fun group(): Group =
        Group
            .create(
                id = groupId("group-1"),
                createdBy = memberEmail("alice"),
                createdAt = Instant.parse("2026-04-01T10:00:00Z"),
            ).addMember(
                member = memberEmail("bob"),
                joinedAt = Instant.parse("2026-04-01T10:00:00Z"),
            ).addMember(
                member = memberEmail("carol"),
                joinedAt = Instant.parse("2026-04-01T10:00:00Z"),
            )

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
