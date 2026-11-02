package tech.justdev.interfaces.group

import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Status
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import tech.justdev.application.auth.AuthenticatedEmailProvider
import tech.justdev.application.group.CancelGroupInvitationCommand
import tech.justdev.application.group.CancelGroupInvitationUseCase
import tech.justdev.domain.group.entity.GroupInvitationId
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.interfaces.openapi.AuthenticatedApi
import java.util.UUID

@Controller("/api/groups/{groupId}/invitations")
@AuthenticatedApi
@Tag(name = "Groups")
class GroupInvitationCancellationController(
    private val useCase: CancelGroupInvitationUseCase,
    private val authenticatedEmailProvider: AuthenticatedEmailProvider,
) {
    @Delete("/{invitationId}")
    @Status(HttpStatus.NO_CONTENT)
    @Operation(operationId = "cancelGroupInvitation", summary = "Cancel a pending invitation as its sender or the group creator")
    suspend fun cancelGroupInvitation(
        groupId: UUID,
        invitationId: UUID,
    ) {
        useCase(
            CancelGroupInvitationCommand(
                GroupId(groupId),
                GroupInvitationId(invitationId),
                authenticatedEmailProvider.currentAuthenticatedEmail(),
            ),
        )
    }
}
