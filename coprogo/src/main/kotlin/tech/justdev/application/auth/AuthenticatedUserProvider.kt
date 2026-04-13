package tech.justdev.application.auth

interface AuthenticatedUserProvider {
    fun currentAuthenticatedUser(): AuthenticatedUser
}
