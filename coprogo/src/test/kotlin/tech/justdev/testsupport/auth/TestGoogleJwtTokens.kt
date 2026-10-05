package tech.justdev.testsupport.auth

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.time.Instant
import java.util.Date

object TestGoogleJwtTokens {
    const val PRIMARY_AUDIENCE = "test-web-client-id.apps.googleusercontent.com"
    const val SECONDARY_AUDIENCE = "test-second-client-id.apps.googleusercontent.com"
    private const val LEGACY_SECRET = "legacy-secret-for-hs256-tests-legacy-secret"
    private const val GOOGLE_ISSUER = "https://accounts.google.com"
    private val rsaJwk = RSAKeyGenerator(2048).keyID("test-google-key").generate()

    fun jwksJson(): String = JWKSet(rsaJwk.toPublicJWK()).toString()

    fun googleIdToken(
        audience: String? = PRIMARY_AUDIENCE,
        additionalAudiences: List<String> = emptyList(),
        issuer: String? = GOOGLE_ISSUER,
        subject: String = "google-subject-123",
        email: String? = "member@example.com",
        emailVerified: Any? = true,
    ): String {
        val now = Instant.now()
        val builder =
            JWTClaimsSet
                .Builder()
                .subject(subject)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
        issuer?.let(builder::issuer)
        audience?.let { primaryAudience ->
            if (additionalAudiences.isEmpty()) {
                builder.audience(primaryAudience)
            } else {
                builder.audience(listOf(primaryAudience) + additionalAudiences)
            }
        }
        email?.let { builder.claim("email", it) }
        emailVerified?.let { builder.claim("email_verified", it) }
        val claims = builder.build()

        return SignedJWT(
            JWSHeader
                .Builder(JWSAlgorithm.RS256)
                .keyID(rsaJwk.keyID)
                .type(JOSEObjectType.JWT)
                .build(),
            claims,
        ).apply {
            sign(RSASSASigner(rsaJwk))
        }.serialize()
    }

    fun legacyJwt(): String {
        val now = Instant.now()
        val claims =
            JWTClaimsSet
                .Builder()
                .issuer("legacy-issuer")
                .audience(PRIMARY_AUDIENCE)
                .subject("legacy-user")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .build()

        return SignedJWT(
            JWSHeader
                .Builder(JWSAlgorithm.HS256)
                .type(JOSEObjectType.JWT)
                .build(),
            claims,
        ).apply {
            sign(MACSigner(LEGACY_SECRET))
        }.serialize()
    }
}
