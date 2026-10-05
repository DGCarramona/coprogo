package tech.justdev.interfaces.expense

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.server.exceptions.ExceptionHandler
import jakarta.inject.Singleton
import tech.justdev.application.expense.SupportingDocumentUploadIntentUnavailableException
import tech.justdev.domain.expense.exception.ExpenseSupportingDocumentUnavailableException
import tech.justdev.interfaces.ApiErrorResponse

@Singleton
class ExpenseSupportingDocumentUnavailableExceptionHandler :
    ExceptionHandler<ExpenseSupportingDocumentUnavailableException, HttpResponse<ApiErrorResponse>> {
    override fun handle(
        request: HttpRequest<*>,
        exception: ExpenseSupportingDocumentUnavailableException,
    ): HttpResponse<ApiErrorResponse> =
        HttpResponse.notFound(
            ApiErrorResponse(message = exception.message ?: "expense supporting document is unavailable", path = request.path),
        )
}

@Singleton
class SupportingDocumentUploadIntentUnavailableExceptionHandler :
    ExceptionHandler<SupportingDocumentUploadIntentUnavailableException, HttpResponse<ApiErrorResponse>> {
    override fun handle(
        request: HttpRequest<*>,
        exception: SupportingDocumentUploadIntentUnavailableException,
    ): HttpResponse<ApiErrorResponse> =
        HttpResponse
            .status<ApiErrorResponse>(HttpStatus.CONFLICT)
            .body(ApiErrorResponse(message = exception.message ?: "supporting document upload intent is unavailable", path = request.path))
}
