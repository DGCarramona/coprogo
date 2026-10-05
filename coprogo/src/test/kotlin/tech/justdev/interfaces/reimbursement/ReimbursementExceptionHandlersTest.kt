package tech.justdev.interfaces.reimbursement

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tech.justdev.application.reimbursement.ReimbursementNotFoundException
import tech.justdev.application.reimbursement.SupportingDocumentUploadIntentUnavailableException
import tech.justdev.domain.reimbursement.valueobject.ReimbursementId
import tech.justdev.interfaces.ApiErrorResponse
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.testUuid

class ReimbursementExceptionHandlersTest {
    @Nested
    inner class ReimbursementNotFound {
        @Test
        fun `should map an unavailable reimbursement to a 404 API response`() {
            val path = "/api/groups/group/reimbursements/reimbursement"
            val exception = ReimbursementNotFoundException(ReimbursementId(testUuid("missing")), groupId("missing-group"))

            val response = ReimbursementNotFoundExceptionHandler().handle(HttpRequest.GET<Any>(path), exception)

            assertEquals(HttpStatus.NOT_FOUND, response.status)
            assertEquals(ApiErrorResponse(exception.message!!, path), response.body())
        }
    }

    @Nested
    inner class SupportingDocumentUploadIntentUnavailable {
        @Test
        fun `should map an unavailable upload intent to a 409 API response`() {
            val path = "/api/groups/group/reimbursements/documented"
            val exception = SupportingDocumentUploadIntentUnavailableException()

            val response =
                ReimbursementSupportingDocumentUploadIntentUnavailableExceptionHandler().handle(
                    HttpRequest.POST(path, Unit),
                    exception,
                )

            assertEquals(HttpStatus.CONFLICT, response.status)
            assertEquals(ApiErrorResponse(exception.message!!, path), response.body())
        }
    }
}
