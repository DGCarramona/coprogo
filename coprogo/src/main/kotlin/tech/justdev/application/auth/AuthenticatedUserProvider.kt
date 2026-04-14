package tech.justdev.application.auth

interface AuthenticatedUserProvider {
    suspend fun currentAuthenticatedUser(): AuthenticatedUser
}
