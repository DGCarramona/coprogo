package tech.justdev.infrastructure.health

import io.micronaut.context.annotation.Property
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
import tech.justdev.testsupport.PostgresMicronautTest
import tech.justdev.testsupport.auth.TestGoogleJwtTokens

@PostgresMicronautTest
class ReadinessEndpointIntegrationTest {
    @Inject
    @field:Client("/")
    lateinit var httpClient: HttpClient

    @Test
    fun `reports readiness with only the status when PostgreSQL is available`() {
        val response = httpClient.toBlocking().exchange(HttpRequest.GET<Any>("/health/readiness"), String::class.java)
        val responseBody = response.body().orEmpty()

        assertEquals(HttpStatus.OK, response.status)
        assertEquals("""{"status":"UP"}""", responseBody)
    }

    @Test
    fun `does not expose database details to authenticated callers`() {
        val request =
            HttpRequest
                .GET<Any>("/health/readiness")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer ${TestGoogleJwtTokens.googleIdToken(email = "readiness.check@example.com")}",
                )
        val response = httpClient.toBlocking().exchange(request, String::class.java)
        val responseBody = response.body().orEmpty()

        assertEquals(HttpStatus.OK, response.status)
        assertEquals("""{"status":"UP"}""", responseBody)
    }

    @Test
    fun `does not make authenticated business routes public`() {
        val exception =
            assertThrows<HttpClientResponseException> {
                httpClient.toBlocking().exchange(HttpRequest.GET<Any>("/test/authenticated-user"), String::class.java)
            }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
    }
}

@NoDbMicronautTest
@Property(name = "r2dbc.datasources.default.enabled", value = "true")
@Property(name = "r2dbc.datasources.default.url", value = "r2dbc:postgresql://127.0.0.1:1/unavailable")
class ReadinessEndpointDatabaseUnavailableIntegrationTest {
    @Inject
    @field:Client("/")
    lateinit var httpClient: HttpClient

    @Test
    fun `reports unavailable database as service unavailable without exposing connection details`() {
        val exception =
            assertThrows<HttpClientResponseException> {
                httpClient.toBlocking().exchange(HttpRequest.GET<Any>("/health/readiness"), String::class.java)
            }

        val responseBody = exception.response.getBody(String::class.java).orElse("")

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.status)
        assertEquals("""{"status":"DOWN"}""", responseBody)
    }
}
