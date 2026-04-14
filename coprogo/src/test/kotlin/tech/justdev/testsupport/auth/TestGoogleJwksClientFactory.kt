package tech.justdev.testsupport.auth

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Replaces
import io.micronaut.security.token.jwt.signature.jwks.JwksClient
import jakarta.inject.Singleton
import reactor.core.publisher.Mono

@Factory
class TestGoogleJwksClientFactory {
    @Singleton
    @Replaces(JwksClient::class)
    fun jwksClient(): JwksClient = JwksClient { _, _ -> Mono.just(TestGoogleJwtTokens.jwksJson()) }
}
