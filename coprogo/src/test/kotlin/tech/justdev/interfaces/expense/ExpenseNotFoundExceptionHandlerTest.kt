package tech.justdev.interfaces.expense

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tech.justdev.application.expense.ExpenseNotFoundException
import tech.justdev.interfaces.ApiErrorResponse
import tech.justdev.testsupport.expenseId
import tech.justdev.testsupport.groupId

class ExpenseNotFoundExceptionHandlerTest {
    @Test
    fun `should map an expense not found exception to a 404 API error response`() {
        val group = groupId("missing-expense-handler-group")
        val id = expenseId("missing-expense-handler")
        val path = "/api/groups/${group.toPrimitive()}/expenses/${id.toPrimitive()}"
        val exception = ExpenseNotFoundException(id = id, group = group)

        val response =
            ExpenseNotFoundExceptionHandler().handle(
                request = HttpRequest.GET<Any>(path),
                exception = exception,
            )

        assertEquals(HttpStatus.NOT_FOUND, response.status)
        assertEquals(
            ApiErrorResponse(
                message = exception.message!!,
                path = path,
            ),
            response.body(),
        )
    }
}
