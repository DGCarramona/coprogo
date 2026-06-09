package tech.justdev.infrastructure.auth

import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.testsupport.UsesPostgresTestDatabase
import tech.justdev.testsupport.auth.TestGoogleJwtTokens

@MicronautTest(transactional = false)
@UsesPostgresTestDatabase
class GoogleIdTokenAuthenticationIntegrationTest {
    @Inject
    @field:Client("/")
    lateinit var httpClient: HttpClient

    @Inject
    lateinit var memberRepository: MemberRepository

    @Test
    fun `accepts a valid Google ID token and exposes the authenticated user through the application port`() {
        val email = "authenticated.member@example.com"
        runTest {
            memberRepository.persist(
                Member(
                    email = MemberEmail.of(email),
                    createdAt = java.time.Instant.parse("2026-04-13T09:00:00Z"),
                ),
            )
        }

        val request =
            HttpRequest
                .GET<Any>("/test/authenticated-user")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestGoogleJwtTokens.googleIdToken(email = email)}")

        val response = httpClient.toBlocking().exchange(request, TestAuthenticatedUserResponse::class.java)

        assertEquals(HttpStatus.OK, response.status)
        assertEquals(
            TestAuthenticatedUserResponse(
                email = email,
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
    fun `accepts a valid Google ID token when the authenticated email is unknown to the system and auto-creates the member`() {
        val email = "unknown.member@example.com"
        val request =
            HttpRequest
                .GET<Any>("/test/authenticated-user")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer ${TestGoogleJwtTokens.googleIdToken(email = email)}",
                )

        val response = httpClient.toBlocking().exchange(request, TestAuthenticatedUserResponse::class.java)

        assertEquals(HttpStatus.OK, response.status)
        assertEquals(TestAuthenticatedUserResponse(email = email), response.body())
        runTest {
            assertEquals(MemberEmail.of(email), memberRepository.findByEmail(MemberEmail.of(email))?.email)
        }
    }

    @Test
    fun `rejects a valid Google ID token when the email claim is not verified`() {
        val request =
            HttpRequest
                .GET<Any>("/test/authenticated-user")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer ${TestGoogleJwtTokens.googleIdToken(emailVerified = false)}",
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
