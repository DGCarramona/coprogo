package tech.justdev.infrastructure.auth.google

import io.micronaut.security.token.Claims
import io.micronaut.security.token.MapClaims
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class GoogleIdTokenClaimsValidatorTest {
    private val validator =
        GoogleIdTokenClaimsValidator(
            GoogleIdTokenConfiguration().apply {
                audiences = listOf(ALLOWED_AUDIENCE)
                issuers = listOf("accounts.google.com")
            },
        )

    @Nested
    inner class Validate {
        @ParameterizedTest(name = "{0}")
        @MethodSource("tech.justdev.infrastructure.auth.google.GoogleIdTokenClaimsValidatorTest#acceptedClaimCases")
        fun `should accept valid required claims`(
            caseName: String,
            claim: String,
            value: Any,
        ) {
            assertTrue(validator.validate(claims(claim to value), null), caseName)
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("tech.justdev.infrastructure.auth.google.GoogleIdTokenClaimsValidatorTest#rejectedClaimCases")
        fun `should reject invalid required claims`(
            caseName: String,
            claim: String,
            value: Any?,
        ) {
            assertFalse(validator.validate(claims(claim to value), null), caseName)
        }
    }

    private fun claims(vararg overrides: Pair<String, Any?>): MapClaims {
        val values =
            mutableMapOf<String, Any>(
                Claims.ISSUER to "accounts.google.com",
                Claims.AUDIENCE to ALLOWED_AUDIENCE,
                GoogleIdTokenClaims.EMAIL to "member@example.com",
                GoogleIdTokenClaims.EMAIL_VERIFIED to true,
            )
        overrides.forEach { (claim, value) ->
            if (value == null) values.remove(claim) else values[claim] = value
        }
        return MapClaims(values)
    }

    private companion object {
        const val ALLOWED_AUDIENCE = "web-client.apps.googleusercontent.com"

        @JvmStatic
        fun acceptedClaimCases(): Stream<Arguments> =
            Stream.of(
                Arguments.of("normalized issuer", Claims.ISSUER, "https://accounts.google.com/"),
                Arguments.of("audience collection", Claims.AUDIENCE, listOf("other", ALLOWED_AUDIENCE, null)),
                Arguments.of("audience array", Claims.AUDIENCE, arrayOf("other", ALLOWED_AUDIENCE)),
                Arguments.of("verified email string", GoogleIdTokenClaims.EMAIL_VERIFIED, "true"),
            )

        @JvmStatic
        fun rejectedClaimCases(): Stream<Arguments> =
            Stream.of(
                Arguments.of("missing issuer", Claims.ISSUER, null),
                Arguments.of("untrusted issuer", Claims.ISSUER, "issuer.example.com"),
                Arguments.of("missing audience", Claims.AUDIENCE, null),
                Arguments.of("blank audience", Claims.AUDIENCE, " "),
                Arguments.of("untrusted audience collection", Claims.AUDIENCE, listOf("other")),
                Arguments.of("missing email", GoogleIdTokenClaims.EMAIL, null),
                Arguments.of("malformed email", GoogleIdTokenClaims.EMAIL, "not-an-email"),
                Arguments.of("missing email verification", GoogleIdTokenClaims.EMAIL_VERIFIED, null),
                Arguments.of("false email verification", GoogleIdTokenClaims.EMAIL_VERIFIED, false),
                Arguments.of("false email verification string", GoogleIdTokenClaims.EMAIL_VERIFIED, "false"),
                Arguments.of("malformed email verification string", GoogleIdTokenClaims.EMAIL_VERIFIED, "not-a-boolean"),
                Arguments.of("numeric email verification", GoogleIdTokenClaims.EMAIL_VERIFIED, 1),
            )
    }
}
