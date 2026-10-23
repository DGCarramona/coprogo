package tech.justdev.interfaces.ledger

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.server.exceptions.ExceptionHandler
import jakarta.inject.Singleton
import tech.justdev.application.ledger.InsufficientCashPoolBalanceException
import tech.justdev.interfaces.ApiErrorResponse

@Singleton
class InsufficientCashPoolBalanceExceptionHandler :
    ExceptionHandler<InsufficientCashPoolBalanceException, HttpResponse<ApiErrorResponse>> {
    override fun handle(
        request: HttpRequest<*>,
        exception: InsufficientCashPoolBalanceException,
    ): HttpResponse<ApiErrorResponse> =
        HttpResponse
            .status<ApiErrorResponse>(HttpStatus.CONFLICT)
            .body(ApiErrorResponse(message = exception.message!!, path = request.path))
}
