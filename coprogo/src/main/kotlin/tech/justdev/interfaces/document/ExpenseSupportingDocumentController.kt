package tech.justdev.interfaces.document

import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
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
import tech.justdev.application.auth.AuthenticatedUserProvider
import tech.justdev.application.document.ExpenseSupportingDocumentAuditAction
import tech.justdev.application.document.ExpenseSupportingDocumentAuditEntrySnapshot
import tech.justdev.application.document.ExpenseSupportingDocumentSnapshot
import tech.justdev.application.document.ListExpenseSupportingDocumentAuditTrailQuery
import tech.justdev.application.document.ListExpenseSupportingDocumentAuditTrailUseCase
import tech.justdev.application.document.ListExpenseSupportingDocumentsQuery
import tech.justdev.application.document.ListExpenseSupportingDocumentsResult
import tech.justdev.application.document.ListExpenseSupportingDocumentsUseCase
import tech.justdev.application.expense.DeleteExpenseSupportingDocumentCommand
import tech.justdev.application.expense.DeleteExpenseSupportingDocumentUseCase
import tech.justdev.application.expense.ReplaceExpenseSupportingDocumentCommand
import tech.justdev.application.expense.ReplaceExpenseSupportingDocumentUseCase
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.interfaces.ApiErrorResponse
import tech.justdev.interfaces.configuration.SupportingDocumentDownloadConfiguration
import tech.justdev.interfaces.openapi.AuthenticatedApi
import java.net.URI
import java.time.Instant
import java.util.UUID

@Controller("/api")
@AuthenticatedApi
@Tag(name = "Supporting documents")
class ExpenseSupportingDocumentController(
    private val authenticatedUserProvider: AuthenticatedUserProvider,
    private val listExpenseSupportingDocumentsUseCase: ListExpenseSupportingDocumentsUseCase,
    private val listExpenseSupportingDocumentAuditTrailUseCase: ListExpenseSupportingDocumentAuditTrailUseCase,
    private val replaceExpenseSupportingDocumentUseCase: ReplaceExpenseSupportingDocumentUseCase,
    private val deleteExpenseSupportingDocumentUseCase: DeleteExpenseSupportingDocumentUseCase,
    private val configuration: SupportingDocumentDownloadConfiguration,
) {
    @Get("/groups/{groupId}/expenses/{expenseId}/supporting-documents")
    @Operation(summary = "List current and historical supporting documents for an expense")
    @ApiResponse(responseCode = "200", description = "Supporting documents")
    suspend fun listExpenseSupportingDocuments(
        @PathVariable groupId: UUID,
        @PathVariable expenseId: UUID,
    ): ExpenseSupportingDocumentsResponse =
        authenticatedUserProvider
            .currentAuthenticatedUser()
            .let { authenticatedUser ->
                ListExpenseSupportingDocumentsQuery(
                    group = GroupId(groupId),
                    expense = ExpenseId(expenseId),
                    requestedBy = authenticatedUser.email,
                    downloadValidFor = configuration.validFor,
                )
            }.let { query -> listExpenseSupportingDocumentsUseCase(query) }
            .toResponse()

    @Get("/groups/{groupId}/expenses/{expenseId}/supporting-document-audit-entries")
    @Operation(
        operationId = "listExpenseSupportingDocumentAuditTrail",
        summary = "List supporting document audit entries for an expense",
    )
    @ApiResponse(responseCode = "200", description = "Supporting document audit entries")
    @ApiResponse(
        responseCode = "404",
        description = "Expense is unavailable",
        content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
    )
    suspend fun listExpenseSupportingDocumentAuditTrail(
        @PathVariable groupId: UUID,
        @PathVariable expenseId: UUID,
    ): List<ExpenseSupportingDocumentAuditEntryResponse> =
        authenticatedUserProvider
            .currentAuthenticatedUser()
            .let { authenticatedUser ->
                ListExpenseSupportingDocumentAuditTrailQuery(
                    group = GroupId(groupId),
                    expense = ExpenseId(expenseId),
                    requestedBy = authenticatedUser.email,
                )
            }.let { query -> listExpenseSupportingDocumentAuditTrailUseCase(query) }
            .map(ExpenseSupportingDocumentAuditEntrySnapshot::toResponse)

    @Post("/groups/{groupId}/expenses/{expenseId}/supporting-documents/{sourceUploadIntent}/replacements")
    @Status(HttpStatus.NO_CONTENT)
    @Operation(
        operationId = "replaceExpenseSupportingDocument",
        summary = "Replace a supporting document for an expense",
    )
    @ApiResponse(responseCode = "204", description = "Supporting document replaced")
    @ApiResponse(
        responseCode = "404",
        description = "Supporting document is unavailable",
        content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
    )
    @ApiResponse(
        responseCode = "409",
        description = "Replacement upload intent is unavailable",
        content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
    )
    suspend fun replaceExpenseSupportingDocument(
        @PathVariable groupId: UUID,
        @PathVariable expenseId: UUID,
        @PathVariable sourceUploadIntent: UUID,
        @Body request: ReplaceExpenseSupportingDocumentRequest,
    ) {
        authenticatedUserProvider
            .currentAuthenticatedUser()
            .let { authenticatedUser ->
                ReplaceExpenseSupportingDocumentCommand(
                    group = GroupId(groupId),
                    expense = ExpenseId(expenseId),
                    replacedSourceUploadIntent = DocumentUploadIntentId(sourceUploadIntent),
                    replacementUploadIntent = DocumentUploadIntentId(request.replacementUploadIntent),
                    requestedBy = authenticatedUser.email,
                    replacedAt = Instant.now(),
                )
            }.let { command -> replaceExpenseSupportingDocumentUseCase(command) }
    }

    @Delete("/groups/{groupId}/expenses/{expenseId}/supporting-documents/{sourceUploadIntent}")
    @Status(HttpStatus.NO_CONTENT)
    @Operation(
        operationId = "deleteExpenseSupportingDocument",
        summary = "Delete a supporting document for an expense",
    )
    @ApiResponse(responseCode = "204", description = "Supporting document deleted")
    @ApiResponse(
        responseCode = "404",
        description = "Supporting document is unavailable",
        content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
    )
    suspend fun deleteExpenseSupportingDocument(
        @PathVariable groupId: UUID,
        @PathVariable expenseId: UUID,
        @PathVariable sourceUploadIntent: UUID,
    ) {
        authenticatedUserProvider
            .currentAuthenticatedUser()
            .let { authenticatedUser ->
                DeleteExpenseSupportingDocumentCommand(
                    group = GroupId(groupId),
                    expense = ExpenseId(expenseId),
                    sourceUploadIntent = DocumentUploadIntentId(sourceUploadIntent),
                    requestedBy = authenticatedUser.email,
                    deletedAt = Instant.now(),
                )
            }.let { command -> deleteExpenseSupportingDocumentUseCase(command) }
    }
}

@Serdeable
data class ReplaceExpenseSupportingDocumentRequest(
    val replacementUploadIntent: UUID,
)

@Serdeable
data class ExpenseSupportingDocumentsResponse(
    val current: List<ExpenseSupportingDocumentResponse>,
    val history: List<ExpenseSupportingDocumentResponse>,
)

@Serdeable
enum class ExpenseSupportingDocumentAuditActionResponse {
    ATTACHED,
    REPLACED,
    DELETED,
}

@Serdeable
data class ExpenseSupportingDocumentAuditEntryResponse(
    val action: ExpenseSupportingDocumentAuditActionResponse,
    val occurredAt: Instant,
    val performedBy: String,
    val documentUploadIntent: UUID,
    val replacedDocumentUploadIntent: UUID?,
    val fileName: String,
)

@Serdeable
data class ExpenseSupportingDocumentResponse(
    val sourceUploadIntent: UUID,
    val fileName: String,
    val mediaType: String,
    val sizeBytes: Long,
    val uploader: String,
    val attachedAt: Instant,
    val replacesSourceUploadIntent: UUID?,
    val deletion: SupportingDocumentDeletionResponse?,
    val canDelete: Boolean,
    val download: SupportingDocumentDownloadResponse,
)

@Serdeable
data class SupportingDocumentDeletionResponse(
    val deletedBy: String,
    val deletedAt: Instant,
)

@Serdeable
data class SupportingDocumentDownloadResponse(
    val url: URI,
    val expiresAt: Instant,
)

private fun ListExpenseSupportingDocumentsResult.toResponse(): ExpenseSupportingDocumentsResponse =
    ExpenseSupportingDocumentsResponse(
        current = current.map(ExpenseSupportingDocumentSnapshot::toResponse),
        history = history.map(ExpenseSupportingDocumentSnapshot::toResponse),
    )

private fun ExpenseSupportingDocumentSnapshot.toResponse(): ExpenseSupportingDocumentResponse =
    ExpenseSupportingDocumentResponse(
        sourceUploadIntent = sourceUploadIntent.toPrimitive(),
        fileName = fileName.toPrimitive(),
        mediaType = mediaType.toPrimitive(),
        sizeBytes = size.toBytes(),
        uploader = uploader.toPrimitive(),
        attachedAt = attachedAt,
        replacesSourceUploadIntent = replacesSourceUploadIntent?.toPrimitive(),
        deletion = deletion?.let { SupportingDocumentDeletionResponse(it.deletedBy.toPrimitive(), it.deletedAt) },
        canDelete = canDelete,
        download = SupportingDocumentDownloadResponse(download.uri, download.expiresAt),
    )

private fun ExpenseSupportingDocumentAuditEntrySnapshot.toResponse(): ExpenseSupportingDocumentAuditEntryResponse =
    ExpenseSupportingDocumentAuditEntryResponse(
        action = action.toResponse(),
        occurredAt = occurredAt,
        performedBy = performedBy.toPrimitive(),
        documentUploadIntent = documentUploadIntent.toPrimitive(),
        replacedDocumentUploadIntent = replacedDocumentUploadIntent?.toPrimitive(),
        fileName = fileName.toPrimitive(),
    )

private fun ExpenseSupportingDocumentAuditAction.toResponse(): ExpenseSupportingDocumentAuditActionResponse =
    when (this) {
        ExpenseSupportingDocumentAuditAction.ATTACHED -> ExpenseSupportingDocumentAuditActionResponse.ATTACHED
        ExpenseSupportingDocumentAuditAction.REPLACED -> ExpenseSupportingDocumentAuditActionResponse.REPLACED
        ExpenseSupportingDocumentAuditAction.DELETED -> ExpenseSupportingDocumentAuditActionResponse.DELETED
    }
