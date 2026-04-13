package tech.justdev.infrastructure.auth

import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.testsupport.NoDbMicronautTest
import tech.justdev.testsupport.auth.TestGoogleJwtTokens

@NoDbMicronautTest
class GoogleIdTokenAuthenticationIntegrationTest {
    @Inject
    @field:Client("/")
    lateinit var httpClient: HttpClient

    @Test
    fun `accepts a valid Google ID token and exposes the authenticated user through the application port`() {
        val request =
            HttpRequest
                .GET<Any>("/test/authenticated-user")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestGoogleJwtTokens.googleIdToken()}")

        val response = httpClient.toBlocking().exchange(request, TestAuthenticatedUserResponse::class.java)

        assertEquals(HttpStatus.OK, response.status)
        assertEquals(
            TestAuthenticatedUserResponse(
                googleSubject = "google-subject-123",
                email = "member@example.com",
                emailVerified = true,
            ),
            response.body(),
        )
    }

    @Test
    fun `rejects a Google-signed token when its audience is not configured`() {
        val request =
            HttpRequest
                .GET<Any>("/test/authenticated-user")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer ${TestGoogleJwtTokens.googleIdToken(audience = "unexpected-client-id.apps.googleusercontent.com")}",
                )

        val exception =
            assertThrows<HttpClientResponseException> {
                httpClient.toBlocking().exchange(request, String::class.java)
            }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
    }

    @Test
    fun `rejects the legacy generic JWT that used the local shared secret`() {
        val request =
            HttpRequest
                .GET<Any>("/test/authenticated-user")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestGoogleJwtTokens.legacyJwt()}")

        val exception =
            assertThrows<HttpClientResponseException> {
                httpClient.toBlocking().exchange(request, String::class.java)
            }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
    }
}
