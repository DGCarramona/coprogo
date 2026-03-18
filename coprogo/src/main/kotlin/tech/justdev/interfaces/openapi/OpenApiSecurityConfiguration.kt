package tech.justdev.interfaces.openapi

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.security.SecurityScheme

const val BEARER_AUTH_SCHEME = "bearerAuth"

@SecurityScheme(
    name = BEARER_AUTH_SCHEME,
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "Google ID token sent in the Authorization header as a Bearer token.",
)
class OpenApiSecurityConfiguration
