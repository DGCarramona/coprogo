package tech.justdev.interfaces

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.server.exceptions.ExceptionHandler
import io.micronaut.serde.annotation.Serdeable
import jakarta.inject.Singleton
import tech.justdev.application.group.GroupAccessDeniedException
import tech.justdev.application.group.GroupApplicationException
import tech.justdev.application.group.GroupInvitationAccessDeniedException
import tech.justdev.application.group.GroupInvitationAlreadyAcceptedException
import tech.justdev.application.group.GroupInvitationAlreadyExistsException
import tech.justdev.application.group.GroupInvitationNotFoundException
import tech.justdev.application.group.GroupNotFoundException
import tech.justdev.application.group.OwnershipShareChangeForbiddenException

@Singleton
class GroupApplicationExceptionHandler : ExceptionHandler<GroupApplicationException, HttpResponse<ApiErrorResponse>> {
    override fun handle(
        request: HttpRequest<*>,
        exception: GroupApplicationException,
    ): HttpResponse<ApiErrorResponse> =
        HttpResponse
            .status<ApiErrorResponse>(exception.toStatus())
            .body(ApiErrorResponse(message = exception.message ?: "application error", path = request.path))
}

@Singleton
class IllegalArgumentApiExceptionHandler : ExceptionHandler<IllegalArgumentException, HttpResponse<ApiErrorResponse>> {
    override fun handle(
        request: HttpRequest<*>,
        exception: IllegalArgumentException,
    ): HttpResponse<ApiErrorResponse> =
        HttpResponse
            .badRequest(ApiErrorResponse(message = exception.message ?: "invalid request", path = request.path))
}

@Serdeable
data class ApiErrorResponse(
    val message: String,
    val path: String,
)

private fun GroupApplicationException.toStatus(): HttpStatus =
    when (this) {
        is GroupAccessDeniedException -> HttpStatus.FORBIDDEN
        is GroupInvitationAccessDeniedException -> HttpStatus.FORBIDDEN
        is OwnershipShareChangeForbiddenException -> HttpStatus.FORBIDDEN
        is GroupNotFoundException -> HttpStatus.NOT_FOUND
        is GroupInvitationNotFoundException -> HttpStatus.NOT_FOUND
        is GroupInvitationAlreadyExistsException -> HttpStatus.CONFLICT
        is GroupInvitationAlreadyAcceptedException -> HttpStatus.CONFLICT
    }
