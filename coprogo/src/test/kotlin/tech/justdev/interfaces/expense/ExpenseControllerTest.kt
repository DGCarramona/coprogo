package tech.justdev.interfaces.expense

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tech.justdev.application.auth.AuthenticatedUser
import tech.justdev.application.auth.AuthenticatedUserProvider
import tech.justdev.application.expense.ExpenseSnapshot
import tech.justdev.application.expense.ListGroupExpensesQuery
import tech.justdev.application.expense.ListGroupExpensesUseCase
import tech.justdev.domain.group.valueobject.MemberEmail
import java.time.Instant
import java.util.UUID

class ExpenseControllerTest {
    private val authProvider = FakeAuthProvider(email = "test@example.com")
    private val useCase = FakeListGroupExpensesUseCase()
    private val controller = ExpenseController(authProvider, useCase)

    @Test
    fun `should map use case result to response`() =
        runTest {
            val groupId = UUID.randomUUID()
            useCase.result = listOf(snapshot("e1"))

            val response = controller.listGroupExpenses(groupId)

            assertEquals(1, response.size)
            assertEquals("e1", response[0].title)
        }

    @Test
    fun `should pass group id to use case`() =
        runTest {
            val groupId = UUID.randomUUID()
            useCase.result = emptyList()

            controller.listGroupExpenses(groupId)

            assertEquals(groupId, useCase.lastQuery!!.group.toPrimitive())
        }

    @Test
    fun `should pass authenticated user email as requestedBy`() =
        runTest {
            useCase.result = emptyList()

            controller.listGroupExpenses(UUID.randomUUID())

            assertEquals("test@example.com", useCase.lastQuery!!.requestedBy.toPrimitive())
        }

    @Test
    fun `should return empty list when use case returns empty`() =
        runTest {
            useCase.result = emptyList()

            val response = controller.listGroupExpenses(UUID.randomUUID())

            assertTrue(response.isEmpty())
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
