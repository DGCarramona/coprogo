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
    fun current(): TestAuthenticatedUserResponse {
        val authenticatedUser = authenticatedUserProvider.currentAuthenticatedUser()

        return TestAuthenticatedUserResponse(
            googleSubject = authenticatedUser.googleSubject,
            email = authenticatedUser.email,
            emailVerified = authenticatedUser.emailVerified,
        )
    }
}

@Serdeable
data class TestAuthenticatedUserResponse(
    val googleSubject: String,
    val email: String?,
    val emailVerified: Boolean,
)
