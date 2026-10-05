package tech.justdev.interfaces.document

import io.micronaut.http.HttpStatus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.application.auth.AuthenticatedUser
import tech.justdev.application.auth.AuthenticatedUserProvider
import tech.justdev.application.document.ConfirmSupportingDocumentUploadCommand
import tech.justdev.application.document.ConfirmSupportingDocumentUploadUseCase
import tech.justdev.application.document.DocumentUploadTarget
import tech.justdev.application.document.StartSupportingDocumentUploadCommand
import tech.justdev.application.document.StartSupportingDocumentUploadResult
import tech.justdev.application.document.StartSupportingDocumentUploadUseCase
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.group.valueobject.MemberEmail
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.UUID

class SupportingDocumentUploadControllerTest {
    private val authProvider = FakeAuthProvider()
    private val startUploadUseCase = FakeStartSupportingDocumentUploadUseCase()
    private val confirmUploadUseCase = FakeConfirmSupportingDocumentUploadUseCase()
    private val controller =
        SupportingDocumentUploadController(
            authenticatedUserProvider = authProvider,
            startSupportingDocumentUploadUseCase = startUploadUseCase,
            confirmSupportingDocumentUploadUseCase = confirmUploadUseCase,
            configuration = SupportingDocumentUploadConfiguration(),
        )

    @Nested
    inner class StartUpload {
        @Test
        fun `should map a request to the authenticated member command and return the signed target`() =
            runTest {
                val groupId = UUID.randomUUID()
                val intentId = UUID.randomUUID()
                val expiresAt = Instant.parse("2026-09-23T12:05:00Z")
                startUploadUseCase.result =
                    StartSupportingDocumentUploadResult(
                        intentId = DocumentUploadIntentId(intentId),
                        target =
                            DocumentUploadTarget(
                                uri = URI("https://storage.example.test/upload"),
                                requiredHeaders = mapOf("content-type" to "application/pdf"),
                                expiresAt = expiresAt,
                            ),
                    )
                val before = Instant.now()

                val response =
                    controller.startUpload(
                        groupId,
                        SupportingDocumentUploadRequest(
                            fileName = " invoice.pdf ",
                            mediaType = " Application/PDF ",
                            sizeBytes = 1234,
                            sha256 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                        ),
                    )

                val after = Instant.now()
                val command = requireNotNull(startUploadUseCase.lastCommand)
                assertEquals(groupId, command.group.toPrimitive())
                assertEquals("member@example.com", command.uploader.toPrimitive())
                assertEquals("invoice.pdf", command.fileName.toPrimitive())
                assertEquals("application/pdf", command.metadata.mediaType.toPrimitive())
                assertEquals(1234, command.metadata.size.toBytes())
                assertEquals("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", command.metadata.checksum.toBase64())
                assertEquals(Duration.ofMinutes(5), command.validFor)
                assertTrue(!command.createdAt.isBefore(before))
                assertTrue(!command.createdAt.isAfter(after))
                assertEquals(HttpStatus.CREATED, response.status)
                assertEquals(
                    "/api/groups/$groupId/supporting-document-uploads/$intentId",
                    response.headers.get("Location"),
                )
                assertEquals(
                    SupportingDocumentUploadResponse(
                        intentId = intentId,
                        uploadUrl = URI("https://storage.example.test/upload"),
                        requiredHeaders = mapOf("content-type" to "application/pdf"),
                        expiresAt = expiresAt,
                    ),
                    response.body(),
                )
            }

        @Test
        fun `should use the configured upload validity duration`() =
            runTest {
                val configuredValidity = Duration.ofSeconds(42)
                val configuredController =
                    SupportingDocumentUploadController(
                        authenticatedUserProvider = authProvider,
                        startSupportingDocumentUploadUseCase = startUploadUseCase,
                        confirmSupportingDocumentUploadUseCase = confirmUploadUseCase,
                        configuration = SupportingDocumentUploadConfiguration().apply { validFor = configuredValidity },
                    )

                configuredController.startUpload(
                    UUID.randomUUID(),
                    SupportingDocumentUploadRequest(
                        fileName = "invoice.pdf",
                        mediaType = "application/pdf",
                        sizeBytes = 1234,
                        sha256 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                    ),
                )

                assertEquals(configuredValidity, requireNotNull(startUploadUseCase.lastCommand).validFor)
            }

        @Test
        fun `should reject an invalid document checksum before calling the use case`() {
            assertThrows<IllegalArgumentException> {
                runTest {
                    controller.startUpload(
                        UUID.randomUUID(),
                        SupportingDocumentUploadRequest(
                            fileName = "invoice.pdf",
                            mediaType = "application/pdf",
                            sizeBytes = 1234,
                            sha256 = "invalid",
                        ),
                    )
                }
            }

            assertNull(startUploadUseCase.lastCommand)
        }
    }

    @Nested
    inner class ConfirmUpload {
        @Test
        fun `should map the group intent and authenticated member to a confirmation command`() =
            runTest {
                val groupId = UUID.randomUUID()
                val intentId = UUID.randomUUID()
                val before = Instant.now()

                controller.confirmUpload(groupId, intentId)

                val after = Instant.now()
                val command = requireNotNull(confirmUploadUseCase.lastCommand)
                assertEquals(groupId, command.group.toPrimitive())
                assertEquals(intentId, command.intent.toPrimitive())
                assertEquals("member@example.com", command.uploader.toPrimitive())
                assertTrue(!command.verifiedAt.isBefore(before))
                assertTrue(!command.verifiedAt.isAfter(after))
            }
    }
}

private class FakeAuthProvider : AuthenticatedUserProvider {
    override suspend fun currentAuthenticatedUser(): AuthenticatedUser = AuthenticatedUser(MemberEmail.of("member@example.com"))
}

private class FakeStartSupportingDocumentUploadUseCase : StartSupportingDocumentUploadUseCase {
    var lastCommand: StartSupportingDocumentUploadCommand? = null
    var result: StartSupportingDocumentUploadResult =
        StartSupportingDocumentUploadResult(
            intentId = DocumentUploadIntentId(UUID.randomUUID()),
            target =
                DocumentUploadTarget(
                    uri = URI("https://storage.example.test/default"),
                    requiredHeaders = emptyMap(),
                    expiresAt = Instant.EPOCH,
                ),
        )

    override suspend fun invoke(command: StartSupportingDocumentUploadCommand): StartSupportingDocumentUploadResult {
        lastCommand = command
        return result
    }
}

private class FakeConfirmSupportingDocumentUploadUseCase : ConfirmSupportingDocumentUploadUseCase {
    var lastCommand: ConfirmSupportingDocumentUploadCommand? = null

    override suspend fun invoke(command: ConfirmSupportingDocumentUploadCommand) {
        lastCommand = command
    }
}
