package tech.justdev.interfaces.expense

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tech.justdev.application.expense.SupportingDocumentUploadIntentUnavailableException
import tech.justdev.interfaces.ApiErrorResponse

class SupportingDocumentUploadIntentUnavailableExceptionHandlerTest {
    @Nested
    inner class Handle {
        @Test
        fun `should map an unavailable supporting document upload intent to a 409 API error response`() {
            val path = "/api/groups/group/expenses/expense/supporting-documents/document/replacements"
            val exception = SupportingDocumentUploadIntentUnavailableException()

            val response =
                SupportingDocumentUploadIntentUnavailableExceptionHandler().handle(
                    request = HttpRequest.POST(path, Unit),
                    exception = exception,
                )

            assertEquals(HttpStatus.CONFLICT, response.status)
            assertEquals(ApiErrorResponse(message = exception.message!!, path = path), response.body())
        }
    }
}
