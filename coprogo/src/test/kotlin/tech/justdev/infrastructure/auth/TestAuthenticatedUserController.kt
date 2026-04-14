package tech.justdev.infrastructure.auth

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.micronaut.serde.annotation.Serdeable
import tech.justdev.application.auth.AuthenticatedUserProvider

@Controller("/test/authenticated-user")
@Secured(SecurityRule.IS_AUTHENTICATED)
class TestAuthenticatedUserController(
    private val authenticatedUserProvider: AuthenticatedUserProvider,
) {
    @Get
    suspend fun current(): TestAuthenticatedUserResponse {
        val authenticatedUser = authenticatedUserProvider.currentAuthenticatedUser()

        return TestAuthenticatedUserResponse(
            email = authenticatedUser.email.toPrimitive(),
        )
    }
}

@Serdeable
data class TestAuthenticatedUserResponse(
    val email: String,
)
