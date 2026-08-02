package tech.justdev.application.expense

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.application.group.GroupAccessDeniedException
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.application.shared.DirectTransactionRunner
import tech.justdev.application.shared.TransactionRunner
import tech.justdev.application.support.InMemoryExpenseRepository
import tech.justdev.application.support.InMemoryGroupRepository
import tech.justdev.application.support.InMemoryLedgerEventRepository
import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.repository.ExpenseRepository
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.expense.valueobject.ExpenseParticipation
import tech.justdev.domain.expense.valueobject.ExpenseParticipationDecision
import tech.justdev.domain.expense.valueobject.ExpenseParticipationStatus
import tech.justdev.domain.expense.valueobject.ExpenseShare
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.ledger.effect.MemberBalanceTransfer
import tech.justdev.domain.ledger.event.AcceptedExpenseLedgerEvent
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.testsupport.acceptedExpenseLedgerEventId
import tech.justdev.testsupport.expenseId
import tech.justdev.testsupport.expenseUuid
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import java.time.Instant

class RecordExpenseParticipationDecisionUseCaseTest {
    @Test
    fun `invoke should append a ledger event when the last pending member approves`() {
        runTest {
            val expenseRepository =
                InMemoryExpenseRepository(
                    expenses = listOf(proposedExpense()),
                )
            val ledgerEventRepository = InMemoryLedgerEventRepository()
            val useCase =
                RecordExpenseParticipationDecisionUseCaseImpl(
                    expenseRepository = expenseRepository,
                    ledgerEventRepository = ledgerEventRepository,
                    groupAccessPolicy = groupAccessPolicy(),
                    transactionRunner = DirectTransactionRunner,
                )

            useCase(
                RecordExpenseParticipationDecisionCommand(
                    group = groupId("group-1"),
                    id = expenseUuid("expense-1"),
                    member = memberEmail("bob"),
                    decision = ExpenseParticipationDecisionCommand.APPROVE,
                    decidedAt = Instant.parse("2026-04-03T12:00:00Z"),
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
                            ExpenseParticipation(
                                member = memberEmail("alice"),
                                amount = MoneyAmount.ofCents(40),
                                status =
                                    ExpenseParticipationStatus.Approved(
                                        Instant.parse("2026-04-03T10:00:00Z"),
                                    ),
                            ),
                            ExpenseParticipation(
                                member = memberEmail("bob"),
                                amount = MoneyAmount.ofCents(60),
                                status =
                                    ExpenseParticipationStatus.Approved(
                                        Instant.parse("2026-04-03T12:00:00Z"),
                                    ),
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
                        paidBy = memberEmail("alice"),
                        occurredAt = Instant.parse("2026-04-03T12:00:00Z"),
                        transfers =
                            setOf(
                                MemberBalanceTransfer(
                                    fromMember = memberEmail("bob"),
                                    toMember = memberEmail("alice"),
                                    amount = MoneyAmount.ofCents(60),
                                ),
                            ),
                    ),
                ),
                ledgerEventRepository.allEvents(),
            )
        }
    }

    @Test
    fun `invoke should invalidate expense without appending a ledger event when a member refuses`() {
        runTest {
            val expenseRepository =
                InMemoryExpenseRepository(
                    expenses = listOf(proposedExpense()),
                )
            val ledgerEventRepository = InMemoryLedgerEventRepository()
            val useCase =
                RecordExpenseParticipationDecisionUseCaseImpl(
                    expenseRepository = expenseRepository,
                    ledgerEventRepository = ledgerEventRepository,
                    groupAccessPolicy = groupAccessPolicy(),
                    transactionRunner = DirectTransactionRunner,
                )

            useCase(
                RecordExpenseParticipationDecisionCommand(
                    group = groupId("group-1"),
                    id = expenseUuid("expense-1"),
                    member = memberEmail("bob"),
                    decision = ExpenseParticipationDecisionCommand.REFUSE,
                    decidedAt = Instant.parse("2026-04-03T12:00:00Z"),
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
                            ExpenseParticipation(
                                member = memberEmail("alice"),
                                amount = MoneyAmount.ofCents(40),
                                status =
                                    ExpenseParticipationStatus.Approved(
                                        Instant.parse("2026-04-03T10:00:00Z"),
                                    ),
                            ),
                            ExpenseParticipation(
                                member = memberEmail("bob"),
                                amount = MoneyAmount.ofCents(60),
                                status =
                                    ExpenseParticipationStatus.Refused(
                                        Instant.parse("2026-04-03T12:00:00Z"),
                                    ),
                            ),
                        ),
                ),
                expenseRepository.findById(expenseId("expense-1")),
            )
            assertEquals(emptyList<AcceptedExpenseLedgerEvent>(), ledgerEventRepository.allEvents())
        }
    }

    @Test
    fun `invoke should fail when the expense is already accepted because only proposed expenses can receive decisions`() {
        val acceptedExpense =
            proposedExpense().recordParticipationDecision(
                member = memberEmail("bob"),
                decision = ExpenseParticipationDecision.APPROVE,
                decidedAt = Instant.parse("2026-04-03T12:00:00Z"),
            )
        val expenseRepository =
            InMemoryExpenseRepository(
                expenses = listOf(acceptedExpense),
            )
        val ledgerEventRepository = InMemoryLedgerEventRepository()
        val useCase =
            RecordExpenseParticipationDecisionUseCaseImpl(
                expenseRepository = expenseRepository,
                ledgerEventRepository = ledgerEventRepository,
                groupAccessPolicy = groupAccessPolicy(),
                transactionRunner = DirectTransactionRunner,
            )

        val error =
            assertThrows<IllegalArgumentException> {
                runTest {
                    useCase(
                        RecordExpenseParticipationDecisionCommand(
                            group = groupId("group-1"),
                            id = expenseUuid("expense-1"),
                            member = memberEmail("bob"),
                            decision = ExpenseParticipationDecisionCommand.APPROVE,
                            decidedAt = Instant.parse("2026-04-03T13:00:00Z"),
                        ),
                    )
                }
            }

        runTest {
            assertEquals("expense ${expenseUuid("expense-1")} was not found", error.message)
            assertEquals(acceptedExpense, expenseRepository.findById(expenseId("expense-1")))
            assertEquals(emptyList<AcceptedExpenseLedgerEvent>(), ledgerEventRepository.allEvents())
        }
    }

    @Test
    fun `invoke should reject a non-member before changing the expense`() {
        val expenseRepository = failIfAccessedExpenseRepository()
        val ledgerEventRepository = InMemoryLedgerEventRepository()
        val useCase =
            RecordExpenseParticipationDecisionUseCaseImpl(
                expenseRepository = expenseRepository,
                ledgerEventRepository = ledgerEventRepository,
                groupAccessPolicy = groupAccessPolicy(),
                transactionRunner = FailIfStartedTransactionRunner,
            )

        assertThrows<GroupAccessDeniedException> {
            runTest {
                useCase(
                    RecordExpenseParticipationDecisionCommand(
                        group = groupId("group-1"),
                        id = expenseUuid("expense-1"),
                        member = memberEmail("outsider"),
                        decision = ExpenseParticipationDecisionCommand.REFUSE,
                        decidedAt = Instant.parse("2026-04-03T12:00:00Z"),
                    ),
                )
            }
        }
        assertEquals(emptyList<AcceptedExpenseLedgerEvent>(), ledgerEventRepository.allEvents())
    }

    @Test
    fun `invoke should reject a decision when the expense belongs to another group`() {
        val expenseRepository = InMemoryExpenseRepository(expenses = listOf(proposedExpense()))
        val ledgerEventRepository = InMemoryLedgerEventRepository()
        val useCase =
            RecordExpenseParticipationDecisionUseCaseImpl(
                expenseRepository = expenseRepository,
                ledgerEventRepository = ledgerEventRepository,
                groupAccessPolicy =
                    groupAccessPolicy(
                        setOf(groupId("group-1"), groupId("group-2")),
                    ),
                transactionRunner = DirectTransactionRunner,
            )

        val error =
            assertThrows<IllegalArgumentException> {
                runTest {
                    useCase(
                        RecordExpenseParticipationDecisionCommand(
                            group = groupId("group-2"),
                            id = expenseUuid("expense-1"),
                            member = memberEmail("bob"),
                            decision = ExpenseParticipationDecisionCommand.APPROVE,
                            decidedAt = Instant.parse("2026-04-03T12:00:00Z"),
                        ),
                    )
                }
            }

        assertEquals(
            "expense ${expenseUuid("expense-1")} was not found",
            error.message,
        )
        runTest {
            assertEquals(proposedExpense(), expenseRepository.findById(expenseId("expense-1")))
        }
        assertEquals(emptyList<AcceptedExpenseLedgerEvent>(), ledgerEventRepository.allEvents())
    }

    private fun groupAccessPolicy(groups: Set<GroupId> = setOf(groupId("group-1"))): GroupAccessPolicy =
        GroupAccessPolicy(
            InMemoryGroupRepository(
                groups.map { group ->
                    Group
                        .create(
                            id = group,
                            createdBy = memberEmail("alice"),
                            createdAt = Instant.parse("2026-04-01T10:00:00Z"),
                        ).addMember(
                            member = memberEmail("bob"),
                            joinedAt = Instant.parse("2026-04-01T10:00:00Z"),
                        )
                },
            ),
        )

    private fun failIfAccessedExpenseRepository(): ExpenseRepository =
        object : ExpenseRepository {
            override suspend fun findById(id: ExpenseId): Expense? = throw AssertionError("expense repository should not be accessed")

            override suspend fun findByGroup(group: GroupId): List<Expense> =
                throw AssertionError("expense repository should not be accessed")

            override suspend fun findProposedByIdAndGroup(
                id: ExpenseId,
                group: GroupId,
            ): Expense? = throw AssertionError("expense repository should not be accessed")

            override suspend fun persist(expense: Expense) = throw AssertionError("expense repository should not be accessed")
        }

    private object FailIfStartedTransactionRunner : TransactionRunner {
        override suspend fun <T> transaction(block: suspend () -> T): T = throw AssertionError("transaction should not be started")
    }

    private fun proposedExpense(): Expense =
        Expense.propose(
            id = expenseId("expense-1"),
            group = groupId("group-1"),
            title = "Boiler repair",
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
