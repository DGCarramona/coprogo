package tech.justdev.infrastructure.auth.google

import io.micronaut.core.annotation.NonNull
import io.micronaut.core.annotation.Nullable
import io.micronaut.security.token.Claims
import io.micronaut.security.token.jwt.validator.GenericJwtClaimsValidator
import jakarta.inject.Singleton

@Singleton
class GoogleIdTokenClaimsValidator(
    private val configuration: GoogleIdTokenConfiguration,
) : GenericJwtClaimsValidator<Any> {
    private val allowedAudiences: Set<String>
        get() = configuration.audiences.toSet()

    private val allowedIssuers: Set<String>
        get() = configuration.issuers.map(::normalizeIssuer).toSet()

    override fun validate(
        @NonNull claims: Claims,
        @Nullable request: Any?,
    ): Boolean {
        val issuer = claims.get(Claims.ISSUER)?.toString()?.let(::normalizeIssuer) ?: return false
        if (issuer !in allowedIssuers) {
            return false
        }

        val audiences = extractAudiences(claims.get(Claims.AUDIENCE))
        if (audiences.isEmpty()) {
            return false
        }

        return audiences.any(allowedAudiences::contains)
    }
}

private fun extractAudiences(audienceClaim: Any?): Set<String> =
    when (audienceClaim) {
        is String -> setOf(audienceClaim)
        is Collection<*> -> audienceClaim.mapNotNull { it?.toString() }.toSet()
        is Array<*> -> audienceClaim.mapNotNull { it?.toString() }.toSet()
        else -> emptySet()
    }.map(String::trim)
        .filter(String::isNotEmpty)
        .toSet()

private fun normalizeIssuer(issuer: String): String = issuer.removePrefix("https://").removeSuffix("/").trim()
