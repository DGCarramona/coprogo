package tech.justdev.interfaces.reimbursement

import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Status
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import tech.justdev.application.auth.AuthenticatedUserProvider
import tech.justdev.application.reimbursement.AcceptReimbursementCommand
import tech.justdev.application.reimbursement.AcceptReimbursementUseCase
import tech.justdev.application.reimbursement.DeclareDocumentedReimbursementCommand
import tech.justdev.application.reimbursement.DeclareDocumentedReimbursementUseCase
import tech.justdev.application.reimbursement.GetReimbursementQuery
import tech.justdev.application.reimbursement.GetReimbursementUseCase
import tech.justdev.application.reimbursement.ListReimbursementsQuery
import tech.justdev.application.reimbursement.ListReimbursementsUseCase
import tech.justdev.application.reimbursement.RecordDirectReimbursementCommand
import tech.justdev.application.reimbursement.RecordDirectReimbursementUseCase
import tech.justdev.application.reimbursement.RejectReimbursementCommand
import tech.justdev.application.reimbursement.RejectReimbursementUseCase
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.reimbursement.valueobject.ReimbursementId
import tech.justdev.domain.reimbursement.valueobject.ReimbursementRejectionReason
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.interfaces.ApiErrorResponse
import tech.justdev.interfaces.openapi.AuthenticatedApi
import java.time.Instant
import java.util.UUID

@Controller("/api")
@AuthenticatedApi
@Tag(name = "Reimbursements")
class ReimbursementController(
    private val authenticatedUserProvider: AuthenticatedUserProvider,
    private val listReimbursementsUseCase: ListReimbursementsUseCase,
    private val getReimbursementUseCase: GetReimbursementUseCase,
    private val recordDirectReimbursementUseCase: RecordDirectReimbursementUseCase,
    private val declareDocumentedReimbursementUseCase: DeclareDocumentedReimbursementUseCase,
    private val acceptReimbursementUseCase: AcceptReimbursementUseCase,
    private val rejectReimbursementUseCase: RejectReimbursementUseCase,
    private val presenter: ReimbursementPresenter,
) {
    @Get("/groups/{groupId}/reimbursements")
    @Operation(summary = "List reimbursements for a group")
    @ApiResponse(responseCode = "200", description = "Reimbursements")
    suspend fun listReimbursements(
        @PathVariable groupId: UUID,
    ): List<ReimbursementResponse> =
        listReimbursementsUseCase(
            ListReimbursementsQuery(
                group = GroupId(groupId),
                requestedBy = authenticatedUserProvider.currentAuthenticatedUser().email,
            ),
        ).map { reimbursement -> presenter.present(reimbursement) }

    @Get("/groups/{groupId}/reimbursements/{reimbursementId}")
    @Operation(summary = "Get reimbursement details")
    @ApiResponse(responseCode = "200", description = "Reimbursement")
    @ApiResponse(
        responseCode = "404",
        description = "Reimbursement is unavailable",
        content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
    )
    suspend fun getReimbursement(
        @PathVariable groupId: UUID,
        @PathVariable reimbursementId: UUID,
    ): ReimbursementResponse =
        getReimbursementUseCase(
            GetReimbursementQuery(
                group = GroupId(groupId),
                reimbursement = ReimbursementId(reimbursementId),
                requestedBy = authenticatedUserProvider.currentAuthenticatedUser().email,
            ),
        ).let { reimbursement -> presenter.present(reimbursement) }

    @Post("/groups/{groupId}/reimbursements/direct")
    @Status(HttpStatus.NO_CONTENT)
    @Operation(summary = "Record a direct reimbursement")
    @ApiResponse(responseCode = "204", description = "Direct reimbursement recorded")
    suspend fun recordDirectReimbursement(
        @PathVariable groupId: UUID,
        @Valid @Body request: DirectReimbursementRequest,
    ) {
        val authenticatedUser = authenticatedUserProvider.currentAuthenticatedUser()

        recordDirectReimbursementUseCase(
            RecordDirectReimbursementCommand(
                group = GroupId(groupId),
                paidBy = MemberEmail.of(request.paidBy),
                recordedBy = authenticatedUser.email,
                amountInCents = request.amountInCents,
                reimbursedAt = request.reimbursedAt,
                recordedAt = Instant.now(),
            ),
        )
    }

    @Post("/groups/{groupId}/reimbursements/documented")
    @Status(HttpStatus.NO_CONTENT)
    @Operation(summary = "Declare a documented reimbursement")
    @ApiResponse(responseCode = "204", description = "Documented reimbursement declared")
    @ApiResponse(
        responseCode = "409",
        description = "Supporting document upload intent is unavailable",
        content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
    )
    suspend fun declareDocumentedReimbursement(
        @PathVariable groupId: UUID,
        @Valid @Body request: DocumentedReimbursementRequest,
    ) {
        val authenticatedUser = authenticatedUserProvider.currentAuthenticatedUser()

        declareDocumentedReimbursementUseCase(
            DeclareDocumentedReimbursementCommand(
                group = GroupId(groupId),
                paidBy = authenticatedUser.email,
                receivedBy = MemberEmail.of(request.receivedBy),
                amountInCents = request.amountInCents,
                reimbursedAt = request.reimbursedAt,
                declaredAt = Instant.now(),
                supportingDocumentUploadIntents =
                    request.supportingDocumentUploadIntents
                        .map(::DocumentUploadIntentId)
                        .toSet(),
            ),
        )
    }

    @Post("/groups/{groupId}/reimbursements/{reimbursementId}/acceptance")
    @Status(HttpStatus.NO_CONTENT)
    @Operation(summary = "Accept a documented reimbursement")
    @ApiResponse(responseCode = "204", description = "Reimbursement accepted")
    @ApiResponse(
        responseCode = "404",
        description = "Reimbursement is unavailable",
        content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
    )
    suspend fun acceptReimbursement(
        @PathVariable groupId: UUID,
        @PathVariable reimbursementId: UUID,
    ) {
        acceptReimbursementUseCase(
            AcceptReimbursementCommand(
                group = GroupId(groupId),
                reimbursement = ReimbursementId(reimbursementId),
                acceptedBy = authenticatedUserProvider.currentAuthenticatedUser().email,
                acceptedAt = Instant.now(),
            ),
        )
    }

    @Post("/groups/{groupId}/reimbursements/{reimbursementId}/rejection")
    @Status(HttpStatus.NO_CONTENT)
    @Operation(summary = "Reject a documented reimbursement")
    @ApiResponse(responseCode = "204", description = "Reimbursement rejected")
    @ApiResponse(
        responseCode = "404",
        description = "Reimbursement is unavailable",
        content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
    )
    suspend fun rejectReimbursement(
        @PathVariable groupId: UUID,
        @PathVariable reimbursementId: UUID,
        @Body request: ReimbursementRejectionRequest,
    ) {
        rejectReimbursementUseCase(
            RejectReimbursementCommand(
                group = GroupId(groupId),
                reimbursement = ReimbursementId(reimbursementId),
                rejectedBy = authenticatedUserProvider.currentAuthenticatedUser().email,
                rejectedAt = Instant.now(),
                reason =
                    request.reason
                        ?.takeUnless(String::isBlank)
                        ?.let(ReimbursementRejectionReason::of),
            ),
        )
    }
}

@Serdeable
data class DirectReimbursementRequest(
    @field:NotBlank
    @field:Email
    val paidBy: String,
    @field:Positive
    val amountInCents: Long,
    val reimbursedAt: Instant,
)

@Serdeable
data class DocumentedReimbursementRequest(
    @field:NotBlank
    @field:Email
    val receivedBy: String,
    @field:Positive
    val amountInCents: Long,
    val reimbursedAt: Instant,
    @field:NotEmpty
    val supportingDocumentUploadIntents: Set<UUID>,
)

@Serdeable
data class ReimbursementRejectionRequest(
    val reason: String? = null,
)
