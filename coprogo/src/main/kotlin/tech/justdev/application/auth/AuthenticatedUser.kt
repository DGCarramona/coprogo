package tech.justdev.application.auth

import tech.justdev.domain.group.valueobject.MemberEmail

data class AuthenticatedUser(
    val email: MemberEmail,
)
