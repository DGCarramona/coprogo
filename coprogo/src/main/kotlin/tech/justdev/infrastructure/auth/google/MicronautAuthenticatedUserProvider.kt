package tech.justdev.infrastructure.auth.google

import io.micronaut.security.authentication.Authentication
import io.micronaut.security.token.Claims
import io.micronaut.security.utils.SecurityService
import jakarta.inject.Singleton
import tech.justdev.application.auth.AuthenticatedUser
import tech.justdev.application.auth.AuthenticatedUserProvider

@Singleton
class MicronautAuthenticatedUserProvider(
    private val securityService: SecurityService,
) : AuthenticatedUserProvider {
    override fun currentAuthenticatedUser(): AuthenticatedUser =
        securityService
            .authentication
            .map(::toAuthenticatedUser)
            .orElseThrow { IllegalStateException("missing authenticated user in request context") }
}

private fun toAuthenticatedUser(authentication: Authentication): AuthenticatedUser {
    val claims = authentication.attributes
    val googleSubject =
        claims[Claims.SUBJECT]
            ?.toString()
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: throw IllegalStateException("authenticated user is missing Google subject claim")

    return AuthenticatedUser(
        googleSubject = googleSubject,
        email = claims[GoogleIdTokenClaims.EMAIL]?.toString()?.trim()?.takeIf(String::isNotEmpty),
        emailVerified = claims[GoogleIdTokenClaims.EMAIL_VERIFIED].toBooleanClaim(),
    )
}

private fun Any?.toBooleanClaim(): Boolean =
    when (this) {
        is Boolean -> this
        is String -> toBooleanStrictOrNull() ?: false
        else -> false
    }
