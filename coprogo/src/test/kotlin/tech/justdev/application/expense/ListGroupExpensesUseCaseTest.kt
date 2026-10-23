package tech.justdev.application.expense

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.application.group.GroupAccessDeniedException
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.application.support.InMemoryGroupRepository
import tech.justdev.domain.expense.valueobject.RefusalReason
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.testsupport.expenseUuid
import tech.justdev.testsupport.groupId
import java.time.Instant

class ListGroupExpensesUseCaseTest {
    private val group = groupId("listed-expenses")
    private val owner = MemberEmail.of("owner@example.com")
    private val outsider = MemberEmail.of("outsider@example.com")
    private val projection = RecordingExpenseListProjection()
    private val useCase =
        ListGroupExpensesUseCaseImpl(
            expenseListProjection = projection,
            groupAccessPolicy = GroupAccessPolicy(InMemoryGroupRepository(listOf(testGroup()))),
        )

    @Nested
    inner class Invoke {
        @Test
        fun `returns the group-scoped summaries supplied by the list projection`() =
            runTest {
                projection.result = listOf(acceptedExpense(), refusedExpense())

                val result = useCase(ListGroupExpensesQuery(group = group, requestedBy = owner))

                assertEquals(listOf(group), projection.queries)
                assertEquals(listOf(acceptedExpense(), refusedExpense()), result)
            }

        @Test
        fun `returns an empty list when the projection has no group expense`() =
            runTest {
                val result = useCase(ListGroupExpensesQuery(group = group, requestedBy = owner))

                assertEquals(emptyList<ExpenseSnapshot>(), result)
                assertEquals(listOf(group), projection.queries)
            }

        @Test
        fun `rejects a non-member before reading the expense projection`() {
            assertThrows<GroupAccessDeniedException> {
                runTest {
                    useCase(ListGroupExpensesQuery(group = group, requestedBy = outsider))
                }
            }

            assertEquals(emptyList<GroupId>(), projection.queries)
        }
    }

    private fun testGroup(): Group =
        Group.create(
            id = group,
            createdBy = owner,
            createdAt = Instant.parse("2026-06-01T08:00:00Z"),
        )

    private fun acceptedExpense(): ExpenseSnapshot =
        ExpenseSnapshot(
            id = expenseUuid("accepted-list-expense"),
            title = "Boiler repair",
            createdBy = owner.toPrimitive(),
            totalAmountCents = 4_200,
            createdAt = Instant.parse("2026-06-01T10:00:00Z"),
            status = "ACCEPTED",
        )

    private fun refusedExpense(): ExpenseSnapshot =
        ExpenseSnapshot(
            id = expenseUuid("refused-list-expense"),
            title = "Roof repair",
            createdBy = owner.toPrimitive(),
            totalAmountCents = 6_000,
            createdAt = Instant.parse("2026-06-02T10:00:00Z"),
            status = "INVALIDATED",
            refusal =
                ExpenseRefusalSnapshot(
                    member = MemberEmail.of("participant@example.com"),
                    refusedAt = Instant.parse("2026-06-03T10:00:00Z"),
                    reason = RefusalReason.of("The work was not agreed"),
                ),
        )
}

private class RecordingExpenseListProjection : ExpenseListProjection {
    var result: List<ExpenseSnapshot> = emptyList()
    val queries = mutableListOf<GroupId>()

    override suspend fun findByGroup(group: GroupId): List<ExpenseSnapshot> = result.also { queries += group }
}
