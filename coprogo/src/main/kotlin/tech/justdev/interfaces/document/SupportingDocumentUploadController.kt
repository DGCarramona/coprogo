package tech.justdev.interfaces.document

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Status
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.PostConstruct
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import tech.justdev.application.auth.AuthenticatedUserProvider
import tech.justdev.application.document.ConfirmSupportingDocumentUploadCommand
import tech.justdev.application.document.ConfirmSupportingDocumentUploadUseCase
import tech.justdev.application.document.StartSupportingDocumentUploadCommand
import tech.justdev.application.document.StartSupportingDocumentUploadResult
import tech.justdev.application.document.StartSupportingDocumentUploadUseCase
import tech.justdev.domain.document.entity.DocumentMetadata
import tech.justdev.domain.document.valueobject.DocumentFileName
import tech.justdev.domain.document.valueobject.DocumentMediaType
import tech.justdev.domain.document.valueobject.DocumentSha256
import tech.justdev.domain.document.valueobject.DocumentSize
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.interfaces.openapi.AuthenticatedApi
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.UUID

@ConfigurationProperties(SupportingDocumentUploadConfiguration.PREFIX)
class SupportingDocumentUploadConfiguration {
    var validFor: Duration = Duration.ofMinutes(5)

    @PostConstruct
    fun validate() {
        require(validFor > Duration.ZERO) { "$PREFIX.valid-for must be strictly positive" }
    }

    companion object {
        const val PREFIX = "coprogo.documents.upload"
    }
}

@Controller("/api")
@AuthenticatedApi
@Tag(name = "Supporting document uploads")
class SupportingDocumentUploadController(
    private val authenticatedUserProvider: AuthenticatedUserProvider,
    private val startSupportingDocumentUploadUseCase: StartSupportingDocumentUploadUseCase,
    private val confirmSupportingDocumentUploadUseCase: ConfirmSupportingDocumentUploadUseCase,
    private val configuration: SupportingDocumentUploadConfiguration,
) {
    @Post("/groups/{groupId}/supporting-document-uploads")
    @Status(HttpStatus.CREATED)
    @Operation(summary = "Start a supporting document upload")
    @ApiResponse(
        responseCode = "201",
        description = "Supporting document upload target created",
        headers = [Header(name = HttpHeaders.LOCATION, schema = Schema(type = "string", format = "uri"))],
    )
    suspend fun startUpload(
        @PathVariable groupId: UUID,
        @Valid @Body request: SupportingDocumentUploadRequest,
    ): HttpResponse<SupportingDocumentUploadResponse> =
        authenticatedUserProvider
            .currentAuthenticatedUser()
            .let { authenticatedUser ->
                StartSupportingDocumentUploadCommand(
                    group = GroupId(groupId),
                    uploader = authenticatedUser.email,
                    fileName = DocumentFileName.of(request.fileName),
                    metadata =
                        DocumentMetadata(
                            mediaType = DocumentMediaType.of(request.mediaType),
                            size = DocumentSize.ofBytes(request.sizeBytes),
                            checksum = DocumentSha256.fromBase64(request.sha256),
                        ),
                    createdAt = Instant.now(),
                    validFor = configuration.validFor,
                )
            }.let { command -> startSupportingDocumentUploadUseCase(command) }
            .let { result -> HttpResponse.created(result.toResponse()).header(HttpHeaders.LOCATION, result.location(groupId)) }

    @Post("/groups/{groupId}/supporting-document-uploads/{uploadIntentId}/confirmation")
    @Status(HttpStatus.NO_CONTENT)
    @Operation(summary = "Confirm a supporting document upload")
    suspend fun confirmUpload(
        @PathVariable groupId: UUID,
        @PathVariable uploadIntentId: UUID,
    ) {
        authenticatedUserProvider
            .currentAuthenticatedUser()
            .let { authenticatedUser ->
                ConfirmSupportingDocumentUploadCommand(
                    group = GroupId(groupId),
                    uploader = authenticatedUser.email,
                    intent = DocumentUploadIntentId(uploadIntentId),
                    verifiedAt = Instant.now(),
                )
            }.let { command -> confirmSupportingDocumentUploadUseCase(command) }
    }
}

@Serdeable
data class SupportingDocumentUploadRequest(
    @field:NotBlank
    val fileName: String,
    @field:NotBlank
    val mediaType: String,
    @field:Positive
    val sizeBytes: Long,
    @field:NotBlank
    val sha256: String,
)

@Serdeable
data class SupportingDocumentUploadResponse(
    val intentId: UUID,
    val uploadUrl: URI,
    val requiredHeaders: Map<String, String>,
    val expiresAt: Instant,
)

private fun StartSupportingDocumentUploadResult.toResponse(): SupportingDocumentUploadResponse =
    SupportingDocumentUploadResponse(
        intentId = intentId.toPrimitive(),
        uploadUrl = target.uri,
        requiredHeaders = target.requiredHeaders,
        expiresAt = target.expiresAt,
    )

private fun StartSupportingDocumentUploadResult.location(groupId: UUID): String =
    "/api/groups/$groupId/supporting-document-uploads/${intentId.toPrimitive()}"
