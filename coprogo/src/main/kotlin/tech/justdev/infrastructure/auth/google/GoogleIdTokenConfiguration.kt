package tech.justdev.infrastructure.auth.google

import io.micronaut.context.annotation.ConfigurationProperties
import jakarta.annotation.PostConstruct

@ConfigurationProperties(GoogleIdTokenConfiguration.PREFIX)
class GoogleIdTokenConfiguration {
    companion object {
        const val PREFIX = "coprogo.security.google-id-token"
        const val DEFAULT_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs"

        val DEFAULT_ISSUERS =
            listOf(
                "accounts.google.com",
                "https://accounts.google.com",
            )
    }

    var audiences: List<String> = emptyList()
    var issuers: List<String> = DEFAULT_ISSUERS
    var jwksUrl: String = DEFAULT_JWKS_URL

    @PostConstruct
    fun validate() {
        audiences = audiences.normalize()
        issuers = issuers.normalize()
        jwksUrl = jwksUrl.trim()

        require(audiences.isNotEmpty()) { "$PREFIX.audiences must contain at least one Google OAuth client id" }
        require(issuers.isNotEmpty()) { "$PREFIX.issuers must contain at least one issuer" }
        require(jwksUrl.isNotBlank()) { "$PREFIX.jwks-url must not be blank" }
    }
}

private fun List<String>.normalize(): List<String> = map(String::trim).filter(String::isNotEmpty)
