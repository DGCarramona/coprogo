package tech.justdev.interfaces.expense

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.application.auth.AuthenticatedUser
import tech.justdev.application.auth.AuthenticatedUserProvider
import tech.justdev.application.expense.EqualSplitExpenseAllocationCommand
import tech.justdev.application.expense.ExpenseDetailParticipationSnapshot
import tech.justdev.application.expense.ExpenseDetailSnapshot
import tech.justdev.application.expense.ExpenseParticipationDecisionCommand
import tech.justdev.application.expense.ExpenseSnapshot
import tech.justdev.application.expense.GetExpenseDetailQuery
import tech.justdev.application.expense.GetExpenseDetailUseCase
import tech.justdev.application.expense.ListGroupExpensesQuery
import tech.justdev.application.expense.ListGroupExpensesUseCase
import tech.justdev.application.expense.ProposeExpenseCommand
import tech.justdev.application.expense.ProposeExpenseUseCase
import tech.justdev.application.expense.RecordExpenseParticipationDecisionCommand
import tech.justdev.application.expense.RecordExpenseParticipationDecisionUseCase
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.expense.valueobject.ExpenseParticipationStatus
import tech.justdev.domain.expense.valueobject.ExpenseStatus
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.testsupport.expenseId
import java.time.Instant
import java.util.UUID

class ExpenseControllerTest {
    private val authProvider = FakeAuthProvider(email = "creator@example.com")
    private val listGroupExpensesUseCase = FakeListGroupExpensesUseCase()
    private val getExpenseDetailUseCase = FakeGetExpenseDetailUseCase()
    private val proposeExpenseUseCase = FakeProposeExpenseUseCase()
    private val recordExpenseParticipationDecisionUseCase = FakeRecordExpenseParticipationDecisionUseCase()
    private val controller =
        ExpenseController(
            authProvider,
            listGroupExpensesUseCase,
            getExpenseDetailUseCase,
            proposeExpenseUseCase,
            recordExpenseParticipationDecisionUseCase,
        )

    @Nested
    inner class ListGroupExpenses {
        @Test
        fun `should map use case result to response`() =
            runTest {
                val groupId = UUID.randomUUID()
                listGroupExpensesUseCase.result = listOf(snapshot("e1"))

                val response = controller.listGroupExpenses(groupId)

                assertEquals(1, response.size)
                assertEquals("e1", response[0].title)
            }

        @Test
        fun `should pass group id to use case`() =
            runTest {
                val groupId = UUID.randomUUID()
                listGroupExpensesUseCase.result = emptyList()

                controller.listGroupExpenses(groupId)

                assertEquals(groupId, listGroupExpensesUseCase.lastQuery!!.group.toPrimitive())
            }

        @Test
        fun `should pass authenticated user email as requestedBy`() =
            runTest {
                listGroupExpensesUseCase.result = emptyList()

                controller.listGroupExpenses(UUID.randomUUID())

                assertEquals("creator@example.com", listGroupExpensesUseCase.lastQuery!!.requestedBy.toPrimitive())
            }

        @Test
        fun `should return empty list when use case returns empty`() =
            runTest {
                listGroupExpensesUseCase.result = emptyList()

                val response = controller.listGroupExpenses(UUID.randomUUID())

                assertTrue(response.isEmpty())
            }
    }

    @Nested
    inner class GetExpenseDetail {
        @Test
        fun `should map the complete expense detail snapshot to the REST response`() =
            runTest {
                val groupId = UUID.randomUUID()
                val expenseId = UUID.randomUUID()
                val createdAt = Instant.parse("2026-07-01T10:00:00Z")
                val approvedAt = Instant.parse("2026-07-02T10:00:00Z")
                val refusedAt = Instant.parse("2026-07-03T10:00:00Z")
                getExpenseDetailUseCase.result =
                    ExpenseDetailSnapshot(
                        id = ExpenseId(expenseId),
                        title = "Roof repair",
                        createdBy = MemberEmail.of("creator@example.com"),
                        totalAmount = MoneyAmount.ofCents(6_000),
                        createdAt = createdAt,
                        status = ExpenseStatus.INVALIDATED,
                        participations =
                            listOf(
                                ExpenseDetailParticipationSnapshot(
                                    member = MemberEmail.of("creator@example.com"),
                                    amount = MoneyAmount.ofCents(2_000),
                                    status = ExpenseParticipationStatus.Approved(approvedAt),
                                ),
                                ExpenseDetailParticipationSnapshot(
                                    member = MemberEmail.of("pending@example.com"),
                                    amount = MoneyAmount.ofCents(2_000),
                                    status = ExpenseParticipationStatus.Pending,
                                ),
                                ExpenseDetailParticipationSnapshot(
                                    member = MemberEmail.of("refused@example.com"),
                                    amount = MoneyAmount.ofCents(2_000),
                                    status = ExpenseParticipationStatus.Refused(refusedAt),
                                ),
                            ),
                    )

                val response = controller.getExpenseDetail(groupId = groupId, expenseId = expenseId)

                assertEquals(
                    ExpenseDetailResponse(
                        id = expenseId,
                        title = "Roof repair",
                        createdBy = "creator@example.com",
                        totalAmountCents = 6_000,
                        createdAt = createdAt,
                        status = "INVALIDATED",
                        participations =
                            listOf(
                                ExpenseDetailParticipationResponse("creator@example.com", 2_000, "APPROVED"),
                                ExpenseDetailParticipationResponse("pending@example.com", 2_000, "PENDING"),
                                ExpenseDetailParticipationResponse("refused@example.com", 2_000, "REFUSED"),
                            ),
                    ),
                    response,
                )
            }

        @Test
        fun `should pass group id expense id and authenticated email to the use case`() =
            runTest {
                val groupId = UUID.randomUUID()
                val expenseId = UUID.randomUUID()
                getExpenseDetailUseCase.result = detailSnapshot(expenseId)

                controller.getExpenseDetail(groupId = groupId, expenseId = expenseId)

                val query = requireNotNull(getExpenseDetailUseCase.lastQuery)
                assertEquals(groupId, query.group.toPrimitive())
                assertEquals(expenseId, query.id.toPrimitive())
                assertEquals("creator@example.com", query.requestedBy.toPrimitive())
            }
    }

    @Nested
    inner class ProposeEqualSplitExpense {
        @Test
        fun `should map the request and normalized participants to a proposal command`() =
            runTest {
                val groupId = UUID.randomUUID()
                val before = Instant.now()

                controller.proposeEqualSplitExpense(
                    groupId = groupId,
                    request =
                        ProposeEqualSplitExpenseRequest(
                            title = "Boiler repair",
                            totalAmountInCents = 12_345,
                            participants = setOf(" CREATOR@EXAMPLE.COM ", " Bob@Example.com "),
                        ),
                )

                val after = Instant.now()
                val command = requireNotNull(proposeExpenseUseCase.lastCommand)
                assertEquals(groupId, command.group.toPrimitive())
                assertEquals("Boiler repair", command.title)
                assertEquals(12_345, command.totalAmountInCents)
                assertEquals("creator@example.com", command.createdBy.toPrimitive())
                assertTrue(!command.createdAt.isBefore(before))
                assertTrue(!command.createdAt.isAfter(after))
                assertEquals(
                    setOf(MemberEmail.of("creator@example.com"), MemberEmail.of("bob@example.com")),
                    (command.allocation as EqualSplitExpenseAllocationCommand).participants,
                )
            }

        @Test
        fun `should reject an invalid participant email before calling the use case`() {
            assertThrows<IllegalArgumentException> {
                runTest {
                    controller.proposeEqualSplitExpense(
                        groupId = UUID.randomUUID(),
                        request =
                            ProposeEqualSplitExpenseRequest(
                                title = "Boiler repair",
                                totalAmountInCents = 12_345,
                                participants = setOf("creator@example.com", "not-an-email"),
                            ),
                    )
                }
            }

            assertEquals(0, proposeExpenseUseCase.invocationCount)
        }
    }

    @Nested
    inner class RecordParticipationDecision {
        @Test
        fun `should map an approval decision to the authenticated member command`() =
            assertDecisionCommandMapped(
                input = ExpenseParticipationDecisionInput.APPROVE,
                expected = ExpenseParticipationDecisionCommand.APPROVE,
            )

        @Test
        fun `should map a refusal decision to the authenticated member command`() =
            assertDecisionCommandMapped(
                input = ExpenseParticipationDecisionInput.REFUSE,
                expected = ExpenseParticipationDecisionCommand.REFUSE,
            )
    }

    private fun assertDecisionCommandMapped(
        input: ExpenseParticipationDecisionInput,
        expected: ExpenseParticipationDecisionCommand,
    ) = runTest {
        val groupId = UUID.randomUUID()
        val expenseId = UUID.randomUUID()
        val before = Instant.now()

        controller.recordParticipationDecision(
            groupId = groupId,
            expenseId = expenseId,
            request = ExpenseParticipationDecisionRequest(input),
        )

        val after = Instant.now()
        val command = requireNotNull(recordExpenseParticipationDecisionUseCase.lastCommand)
        assertEquals(groupId, command.group.toPrimitive())
        assertEquals(expenseId, command.id)
        assertEquals("creator@example.com", command.member.toPrimitive())
        assertEquals(expected, command.decision)
        assertTrue(!command.decidedAt.isBefore(before))
        assertTrue(!command.decidedAt.isAfter(after))
    }

    private fun snapshot(seed: String): ExpenseSnapshot =
        ExpenseSnapshot(
            id = UUID.randomUUID(),
            title = seed,
            createdBy = "creator@example.com",
            totalAmountCents = 1000,
            createdAt = Instant.parse("2026-06-01T10:00:00Z"),
            status = "PROPOSED",
        )

    private fun detailSnapshot(id: UUID): ExpenseDetailSnapshot =
        ExpenseDetailSnapshot(
            id = ExpenseId(id),
            title = "Roof repair",
            createdBy = MemberEmail.of("creator@example.com"),
            totalAmount = MoneyAmount.ofCents(1_000),
            createdAt = Instant.parse("2026-07-01T10:00:00Z"),
            status = ExpenseStatus.PROPOSED,
            participations = emptyList(),
        )
}

private class FakeAuthProvider(
    private val email: String,
) : AuthenticatedUserProvider {
    override suspend fun currentAuthenticatedUser(): AuthenticatedUser = AuthenticatedUser(MemberEmail.of(email))
}

private class FakeListGroupExpensesUseCase : ListGroupExpensesUseCase {
    var result: List<ExpenseSnapshot> = emptyList()
    var lastQuery: ListGroupExpensesQuery? = null

    override suspend fun invoke(query: ListGroupExpensesQuery): List<ExpenseSnapshot> {
        lastQuery = query
        return result
    }
}

private class FakeGetExpenseDetailUseCase : GetExpenseDetailUseCase {
    var result: ExpenseDetailSnapshot =
        ExpenseDetailSnapshot(
            id = expenseId("default-expense-detail-controller"),
            title = "default",
            createdBy = MemberEmail.of("creator@example.com"),
            totalAmount = MoneyAmount.ofCents(1),
            createdAt = Instant.EPOCH,
            status = ExpenseStatus.PROPOSED,
            participations = emptyList(),
        )
    var lastQuery: GetExpenseDetailQuery? = null

    override suspend fun invoke(query: GetExpenseDetailQuery): ExpenseDetailSnapshot {
        lastQuery = query
        return result
    }
}

private class FakeProposeExpenseUseCase : ProposeExpenseUseCase {
    var invocationCount = 0
    var lastCommand: ProposeExpenseCommand? = null

    override suspend fun invoke(command: ProposeExpenseCommand) {
        invocationCount += 1
        lastCommand = command
    }
}

private class FakeRecordExpenseParticipationDecisionUseCase : RecordExpenseParticipationDecisionUseCase {
    var lastCommand: RecordExpenseParticipationDecisionCommand? = null

    override suspend fun invoke(command: RecordExpenseParticipationDecisionCommand) {
        lastCommand = command
    }
}
