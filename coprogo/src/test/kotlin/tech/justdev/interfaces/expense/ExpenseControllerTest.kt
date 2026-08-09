package tech.justdev.interfaces.expense

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.application.auth.AuthenticatedUser
import tech.justdev.application.auth.AuthenticatedUserProvider
import tech.justdev.application.expense.CumulativeExpenseTierCommand
import tech.justdev.application.expense.CumulativeTiersExpenseAllocationCommand
import tech.justdev.application.expense.ExpenseDetailParticipationSnapshot
import tech.justdev.application.expense.ExpenseDetailSnapshot
import tech.justdev.application.expense.ExpenseParticipationDecisionCommand
import tech.justdev.application.expense.ExpenseRefusalSnapshot
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
import tech.justdev.domain.expense.valueobject.RefusalReason
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
        fun `should map the refusal snapshot to the list response`() =
            runTest {
                val refusedAt = Instant.parse("2026-07-03T10:00:00Z")
                listGroupExpensesUseCase.result =
                    listOf(
                        ExpenseSnapshot(
                            id = UUID.randomUUID(),
                            title = "Roof repair",
                            createdBy = "creator@example.com",
                            totalAmountCents = 6_000,
                            createdAt = Instant.parse("2026-07-01T10:00:00Z"),
                            status = "INVALIDATED",
                            refusal =
                                ExpenseRefusalSnapshot(
                                    member = MemberEmail.of("refused@example.com"),
                                    refusedAt = refusedAt,
                                    reason = RefusalReason.of("The work was not agreed"),
                                ),
                        ),
                    )

                val response = controller.listGroupExpenses(UUID.randomUUID()).single()

                assertEquals(
                    ExpenseRefusalResponse(
                        member = "refused@example.com",
                        refusedAt = refusedAt,
                        reason = "The work was not agreed",
                    ),
                    response.refusal,
                )
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
                        refusal =
                            ExpenseRefusalSnapshot(
                                member = MemberEmail.of("refused@example.com"),
                                refusedAt = refusedAt,
                                reason = null,
                            ),
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
                        refusal =
                            ExpenseRefusalResponse(
                                member = "refused@example.com",
                                refusedAt = refusedAt,
                                reason = null,
                            ),
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
    inner class ProposeExpense {
        @Test
        fun `should map a cumulative tiers request to a proposal command`() =
            runTest {
                val groupId = UUID.randomUUID()
                val before = Instant.now()

                controller.proposeExpense(
                    groupId = groupId,
                    request =
                        ProposeExpenseRequest(
                            title = "Boiler repair",
                            totalAmountInCents = 12_345,
                            allocation =
                                CumulativeTiersExpenseAllocationRequest(
                                    tiers =
                                        listOf(
                                            CumulativeExpenseTierRequest(
                                                upToAmountInCents = 12_345,
                                                participants = setOf(" CREATOR@EXAMPLE.COM ", " Bob@Example.com "),
                                            ),
                                        ),
                                ),
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
                    CumulativeTiersExpenseAllocationCommand(
                        tiers =
                            listOf(
                                CumulativeExpenseTierCommand(
                                    upToAmountInCents = 12_345,
                                    participants =
                                        setOf(
                                            MemberEmail.of("creator@example.com"),
                                            MemberEmail.of("bob@example.com"),
                                        ),
                                ),
                            ),
                    ),
                    command.allocation,
                )
            }

        @Test
        fun `should reject an invalid participant email before calling the use case`() {
            assertThrows<IllegalArgumentException> {
                runTest {
                    controller.proposeExpense(
                        groupId = UUID.randomUUID(),
                        request =
                            ProposeExpenseRequest(
                                title = "Boiler repair",
                                totalAmountInCents = 12_345,
                                allocation =
                                    CumulativeTiersExpenseAllocationRequest(
                                        tiers =
                                            listOf(
                                                CumulativeExpenseTierRequest(
                                                    upToAmountInCents = 12_345,
                                                    participants = setOf("creator@example.com", "not-an-email"),
                                                ),
                                            ),
                                    ),
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

        @Test
        fun `should map a non-blank refusal reason to the domain value object`() =
            assertDecisionCommandMapped(
                input = ExpenseParticipationDecisionInput.REFUSE,
                expected = ExpenseParticipationDecisionCommand.REFUSE,
                reason = "The amount does not match the invoice",
                expectedReason = RefusalReason.of("The amount does not match the invoice"),
            )

        @Test
        fun `should normalize a blank refusal reason to absence`() =
            assertDecisionCommandMapped(
                input = ExpenseParticipationDecisionInput.REFUSE,
                expected = ExpenseParticipationDecisionCommand.REFUSE,
                reason = "  \t ",
                expectedReason = null,
            )
    }

    private fun assertDecisionCommandMapped(
        input: ExpenseParticipationDecisionInput,
        expected: ExpenseParticipationDecisionCommand,
        reason: String? = null,
        expectedReason: RefusalReason? = null,
    ) = runTest {
        val groupId = UUID.randomUUID()
        val expenseId = UUID.randomUUID()
        val before = Instant.now()

        controller.recordParticipationDecision(
            groupId = groupId,
            expenseId = expenseId,
            request = ExpenseParticipationDecisionRequest(decision = input, reason = reason),
        )

        val after = Instant.now()
        val command = requireNotNull(recordExpenseParticipationDecisionUseCase.lastCommand)
        assertEquals(groupId, command.group.toPrimitive())
        assertEquals(expenseId, command.id)
        assertEquals("creator@example.com", command.member.toPrimitive())
        assertEquals(expected, command.decision)
        assertEquals(expectedReason, command.reason)
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
