package tech.justdev.interfaces.expense

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.server.exceptions.ExceptionHandler
import jakarta.inject.Singleton
import tech.justdev.application.expense.ExpenseNotFoundException
import tech.justdev.interfaces.ApiErrorResponse

@Singleton
class ExpenseNotFoundExceptionHandler : ExceptionHandler<ExpenseNotFoundException, HttpResponse<ApiErrorResponse>> {
    override fun handle(
        request: HttpRequest<*>,
        exception: ExpenseNotFoundException,
    ): HttpResponse<ApiErrorResponse> =
        HttpResponse.notFound(
            ApiErrorResponse(
                message = exception.message ?: "expense not found",
                path = request.path,
            ),
        )
}
