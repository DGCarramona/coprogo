package tech.justdev.interfaces.group

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tech.justdev.application.auth.AuthenticatedEmailProvider
import tech.justdev.application.group.CancelGroupInvitationCommand
import tech.justdev.application.group.CancelGroupInvitationUseCase
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.groupInvitationId
import tech.justdev.testsupport.memberEmail

class GroupInvitationCancellationControllerTest {
    @Test
    fun `cancelGroupInvitation uses authenticated actor and route identifiers`() =
        runTest {
            val commands = mutableListOf<CancelGroupInvitationCommand>()
            val useCase =
                object : CancelGroupInvitationUseCase {
                    override suspend fun invoke(command: CancelGroupInvitationCommand) {
                        commands.add(command)
                    }
                }
            val actor = memberEmail("alice")
            val auth =
                object : AuthenticatedEmailProvider {
                    override suspend fun currentAuthenticatedEmail() = actor
                }
            GroupInvitationCancellationController(useCase, auth).cancelGroupInvitation(
                groupId("one").toPrimitive(),
                groupInvitationId("one").toPrimitive(),
            )
            assertEquals(listOf(CancelGroupInvitationCommand(groupId("one"), groupInvitationId("one"), actor)), commands)
        }
}
