package tech.justdev.application.auth

import tech.justdev.domain.group.valueobject.MemberEmail

interface AuthenticatedEmailProvider {
    suspend fun currentAuthenticatedEmail(): MemberEmail
}
