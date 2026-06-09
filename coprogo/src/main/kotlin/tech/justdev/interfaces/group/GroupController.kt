package tech.justdev.interfaces.group

import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Status
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import tech.justdev.application.auth.AuthenticatedEmailProvider
import tech.justdev.application.auth.AuthenticatedUserProvider
import tech.justdev.application.group.AcceptGroupInvitationCommand
import tech.justdev.application.group.AcceptGroupInvitationUseCase
import tech.justdev.application.group.CreateGroupCommand
import tech.justdev.application.group.CreateGroupUseCase
import tech.justdev.application.group.GetGroupQuery
import tech.justdev.application.group.GetGroupUseCase
import tech.justdev.application.group.GroupInvitationSnapshot
import tech.justdev.application.group.GroupMemberSnapshot
import tech.justdev.application.group.GroupSnapshot
import tech.justdev.application.group.InviteMemberToGroupCommand
import tech.justdev.application.group.InviteMemberToGroupUseCase
import tech.justdev.application.group.ListPendingGroupInvitationsQuery
import tech.justdev.application.group.ListPendingGroupInvitationsUseCase
import tech.justdev.application.group.PendingGroupInvitationSnapshot
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.interfaces.openapi.AuthenticatedApi
import java.time.Instant
import java.util.UUID

@Controller("/api")
@AuthenticatedApi
@Tag(name = "Groups")
class GroupController(
    private val authenticatedEmailProvider: AuthenticatedEmailProvider,
    private val authenticatedUserProvider: AuthenticatedUserProvider,
    private val createGroupUseCase: CreateGroupUseCase,
    private val getGroupUseCase: GetGroupUseCase,
    private val inviteMemberToGroupUseCase: InviteMemberToGroupUseCase,
    private val acceptGroupInvitationUseCase: AcceptGroupInvitationUseCase,
    private val listPendingGroupInvitationsUseCase: ListPendingGroupInvitationsUseCase,
) {
    @Post("/groups")
    @Operation(summary = "Create a new group for the authenticated member")
    suspend fun create(): HttpResponse<CreateGroupResponse> {
        val authenticatedUser = authenticatedUserProvider.currentAuthenticatedUser()
        val createdGroup =
            createGroupUseCase(
                CreateGroupCommand(
                    createdBy = authenticatedUser.email,
                    createdAt = Instant.now(),
                ),
            )

        return HttpResponse
            .status<CreateGroupResponse>(HttpStatus.CREATED)
            .header(HttpHeaders.LOCATION, "/api/groups/${createdGroup.group}")
            .body(CreateGroupResponse(group = createdGroup.group))
    }

    @Get("/groups/{groupId}")
    @Operation(summary = "Get the group details for the authenticated member")
    suspend fun get(
        @PathVariable groupId: UUID,
    ): GroupResponse {
        val authenticatedUser = authenticatedUserProvider.currentAuthenticatedUser()
        val group =
            getGroupUseCase(
                GetGroupQuery(
                    group = groupId,
                    requestedBy = authenticatedUser.email,
                ),
            )

        return group.toResponse()
    }

    @Post("/groups/{groupId}/invitations")
    @Status(HttpStatus.NO_CONTENT)
    @Operation(summary = "Invite a member to a group")
    suspend fun invite(
        @PathVariable groupId: UUID,
        @Valid @Body request: InviteMemberRequest,
    ) {
        val authenticatedUser = authenticatedUserProvider.currentAuthenticatedUser()
        inviteMemberToGroupUseCase(
            InviteMemberToGroupCommand(
                group = groupId,
                invitedBy = authenticatedUser.email,
                invitedMember = MemberEmail.of(request.invitedMember),
                invitedAt = Instant.now(),
            ),
        )
    }

    @Post("/group-invitations/{invitationId}/accept")
    @Status(HttpStatus.NO_CONTENT)
    @Operation(summary = "Accept a pending group invitation")
    suspend fun accept(
        @PathVariable invitationId: UUID,
    ) {
        acceptGroupInvitationUseCase(
            AcceptGroupInvitationCommand(
                invitation = invitationId,
                acceptedBy = authenticatedEmailProvider.currentAuthenticatedEmail(),
                acceptedAt = Instant.now(),
            ),
        )
    }

    @Get("/group-invitations/pending")
    @Operation(summary = "List the pending invitations for the authenticated member")
    suspend fun listPending(): List<PendingGroupInvitationResponse> {
        val authenticatedUser = authenticatedUserProvider.currentAuthenticatedUser()

        return listPendingGroupInvitationsUseCase(
            ListPendingGroupInvitationsQuery(
                requestedBy = authenticatedUser.email,
            ),
        ).map(PendingGroupInvitationSnapshot::toResponse)
    }
}

@Serdeable
data class CreateGroupResponse(
    val group: UUID,
)

@Serdeable
data class InviteMemberRequest(
    @field:NotBlank
    val invitedMember: String,
)

@Serdeable
data class GroupResponse(
    val group: UUID,
    val createdBy: String,
    val createdAt: Instant,
    val members: List<GroupMemberResponse>,
    val pendingInvitations: List<GroupInvitationResponse>,
)

@Serdeable
data class GroupMemberResponse(
    val member: String,
    val joinedAt: Instant,
)

@Serdeable
data class GroupInvitationResponse(
    val invitation: UUID,
    val invitedMember: String,
    val invitedBy: String,
    val invitedAt: Instant,
)

@Serdeable
data class PendingGroupInvitationResponse(
    val invitation: UUID,
    val group: UUID,
    val invitedMember: String,
    val invitedBy: String,
    val invitedAt: Instant,
)

private fun GroupSnapshot.toResponse(): GroupResponse =
    GroupResponse(
        group = group,
        createdBy = createdBy,
        createdAt = createdAt,
        members = members.map(GroupMemberSnapshot::toResponse),
        pendingInvitations = pendingInvitations.map(GroupInvitationSnapshot::toResponse),
    )

private fun GroupMemberSnapshot.toResponse(): GroupMemberResponse =
    GroupMemberResponse(
        member = member,
        joinedAt = joinedAt,
    )

private fun GroupInvitationSnapshot.toResponse(): GroupInvitationResponse =
    GroupInvitationResponse(
        invitation = invitation,
        invitedMember = invitedMember,
        invitedBy = invitedBy,
        invitedAt = invitedAt,
    )

private fun PendingGroupInvitationSnapshot.toResponse(): PendingGroupInvitationResponse =
    PendingGroupInvitationResponse(
        invitation = invitation,
        group = group,
        invitedMember = invitedMember,
        invitedBy = invitedBy,
        invitedAt = invitedAt,
    )
