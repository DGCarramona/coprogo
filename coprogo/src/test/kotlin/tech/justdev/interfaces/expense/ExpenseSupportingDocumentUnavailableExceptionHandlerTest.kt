package tech.justdev.interfaces.expense

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tech.justdev.domain.expense.exception.ExpenseSupportingDocumentUnavailableException
import tech.justdev.interfaces.ApiErrorResponse

class ExpenseSupportingDocumentUnavailableExceptionHandlerTest {
    @Nested
    inner class Handle {
        @Test
        fun `should map an unavailable expense supporting document to a 404 API error response`() {
            val path = "/api/groups/group/expenses/expense/supporting-documents/document"
            val exception = ExpenseSupportingDocumentUnavailableException()

            val response =
                ExpenseSupportingDocumentUnavailableExceptionHandler().handle(
                    request = HttpRequest.DELETE<Any>(path),
                    exception = exception,
                )

            assertEquals(HttpStatus.NOT_FOUND, response.status)
            assertEquals(ApiErrorResponse(message = exception.message!!, path = path), response.body())
        }
    }
}
