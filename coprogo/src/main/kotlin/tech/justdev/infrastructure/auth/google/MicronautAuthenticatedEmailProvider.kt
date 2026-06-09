package tech.justdev.infrastructure.auth.google

import io.micronaut.http.HttpStatus
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.utils.SecurityService
import jakarta.inject.Singleton
import tech.justdev.application.auth.AuthenticatedEmailProvider
import tech.justdev.domain.group.valueobject.MemberEmail

@Singleton
class MicronautAuthenticatedEmailProvider(
    private val securityService: SecurityService,
) : AuthenticatedEmailProvider {
    override suspend fun currentAuthenticatedEmail(): MemberEmail {
        val authentication =
            securityService.authentication.orElseThrow {
                IllegalStateException("missing authenticated user in request context")
            }

        if (!authentication.attributes[GoogleIdTokenClaims.EMAIL_VERIFIED].toBooleanClaim()) {
            throw unauthorized("authenticated user email is not verified")
        }

        return authentication.toAuthenticatedEmail()
    }
}

private fun Authentication.toAuthenticatedEmail(): MemberEmail {
    val rawEmail =
        attributes[GoogleIdTokenClaims.EMAIL]
            ?.toString()
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: throw unauthorized("authenticated user is missing email claim")

    return try {
        MemberEmail.of(rawEmail)
    } catch (_: IllegalArgumentException) {
        throw unauthorized("authenticated user email claim is invalid")
    }
}

private fun Any?.toBooleanClaim(): Boolean =
    when (this) {
        is Boolean -> this
        is String -> toBooleanStrictOrNull() ?: false
        else -> false
    }

private fun unauthorized(message: String): HttpStatusException = HttpStatusException(HttpStatus.UNAUTHORIZED, message)
