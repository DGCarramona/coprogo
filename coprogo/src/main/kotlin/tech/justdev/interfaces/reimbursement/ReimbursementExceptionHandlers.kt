package tech.justdev.interfaces.reimbursement

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.server.exceptions.ExceptionHandler
import jakarta.inject.Singleton
import tech.justdev.application.reimbursement.ReimbursementNotFoundException
import tech.justdev.application.reimbursement.SupportingDocumentUploadIntentUnavailableException
import tech.justdev.interfaces.ApiErrorResponse

@Singleton
class ReimbursementNotFoundExceptionHandler : ExceptionHandler<ReimbursementNotFoundException, HttpResponse<ApiErrorResponse>> {
    override fun handle(
        request: HttpRequest<*>,
        exception: ReimbursementNotFoundException,
    ): HttpResponse<ApiErrorResponse> =
        HttpResponse.notFound(ApiErrorResponse(exception.message ?: "reimbursement not found", request.path))
}

@Singleton
class ReimbursementSupportingDocumentUploadIntentUnavailableExceptionHandler :
    ExceptionHandler<SupportingDocumentUploadIntentUnavailableException, HttpResponse<ApiErrorResponse>> {
    override fun handle(
        request: HttpRequest<*>,
        exception: SupportingDocumentUploadIntentUnavailableException,
    ): HttpResponse<ApiErrorResponse> =
        HttpResponse
            .status<ApiErrorResponse>(HttpStatus.CONFLICT)
            .body(ApiErrorResponse(exception.message ?: "supporting document upload intent is unavailable", request.path))
}
