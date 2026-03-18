package tech.justdev.interfaces.openapi

import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.swagger.v3.oas.annotations.security.SecurityRequirement

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Secured(SecurityRule.IS_AUTHENTICATED)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
annotation class AuthenticatedApi
