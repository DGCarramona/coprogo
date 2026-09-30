package tech.justdev.infrastructure.auth.google

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GoogleIdTokenConfigurationTest {
    @Nested
    inner class Validate {
        @Test
        fun `should trim and discard blank audience and issuer entries`() {
            val configuration =
                GoogleIdTokenConfiguration().apply {
                    audiences = listOf(" ", " web-client.apps.googleusercontent.com ")
                    issuers = listOf("", " accounts.google.com ")
                    jwksUrl = " https://www.googleapis.com/oauth2/v3/certs "
                }

            configuration.validate()

            assertEquals(listOf("web-client.apps.googleusercontent.com"), configuration.audiences)
            assertEquals(listOf("accounts.google.com"), configuration.issuers)
            assertEquals("https://www.googleapis.com/oauth2/v3/certs", configuration.jwksUrl)
        }

        @Test
        fun `should reject configuration without a non blank audience`() {
            val configuration = GoogleIdTokenConfiguration().apply { audiences = listOf("", " ") }

            val error = assertThrows<IllegalArgumentException> { configuration.validate() }

            assertEquals(
                "${GoogleIdTokenConfiguration.PREFIX}.audiences must contain at least one Google OAuth client id",
                error.message,
            )
        }

        @Test
        fun `should reject configuration without a non blank issuer`() {
            val configuration =
                GoogleIdTokenConfiguration().apply {
                    audiences = listOf("web-client.apps.googleusercontent.com")
                    issuers = listOf("", " ")
                }

            val error = assertThrows<IllegalArgumentException> { configuration.validate() }

            assertEquals("${GoogleIdTokenConfiguration.PREFIX}.issuers must contain at least one issuer", error.message)
        }

        @Test
        fun `should reject a blank JWKS URL`() {
            val configuration =
                GoogleIdTokenConfiguration().apply {
                    audiences = listOf("web-client.apps.googleusercontent.com")
                    jwksUrl = " "
                }

            val error = assertThrows<IllegalArgumentException> { configuration.validate() }

            assertEquals("${GoogleIdTokenConfiguration.PREFIX}.jwks-url must not be blank", error.message)
        }
    }
}
