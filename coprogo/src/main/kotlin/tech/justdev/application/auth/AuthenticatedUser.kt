package tech.justdev.application.auth

data class AuthenticatedUser(
    val googleSubject: String,
    val email: String?,
    val emailVerified: Boolean,
)
