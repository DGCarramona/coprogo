package tech.justdev.interfaces.document

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.PostConstruct
import tech.justdev.application.auth.AuthenticatedUserProvider
import tech.justdev.application.document.ExpenseSupportingDocumentSnapshot
import tech.justdev.application.document.ListExpenseSupportingDocumentsQuery
import tech.justdev.application.document.ListExpenseSupportingDocumentsResult
import tech.justdev.application.document.ListExpenseSupportingDocumentsUseCase
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.interfaces.openapi.AuthenticatedApi
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.UUID

@ConfigurationProperties(SupportingDocumentDownloadConfiguration.PREFIX)
class SupportingDocumentDownloadConfiguration {
    var validFor: Duration = Duration.ofMinutes(5)

    @PostConstruct
    fun validate() {
        require(validFor > Duration.ZERO) { "$PREFIX.valid-for must be strictly positive" }
    }

    companion object {
        const val PREFIX = "coprogo.documents.download"
    }
}

@Controller("/api")
@AuthenticatedApi
@Tag(name = "Supporting documents")
class ExpenseSupportingDocumentController(
    private val authenticatedUserProvider: AuthenticatedUserProvider,
    private val listExpenseSupportingDocumentsUseCase: ListExpenseSupportingDocumentsUseCase,
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
}

@Serdeable
data class ExpenseSupportingDocumentsResponse(
    val current: List<ExpenseSupportingDocumentResponse>,
    val history: List<ExpenseSupportingDocumentResponse>,
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
        download = SupportingDocumentDownloadResponse(download.uri, download.expiresAt),
    )
