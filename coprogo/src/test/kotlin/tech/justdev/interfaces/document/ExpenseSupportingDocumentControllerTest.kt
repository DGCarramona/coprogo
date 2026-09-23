package tech.justdev.interfaces.document

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tech.justdev.application.auth.AuthenticatedUser
import tech.justdev.application.auth.AuthenticatedUserProvider
import tech.justdev.application.document.DocumentDownloadTarget
import tech.justdev.application.document.ExpenseSupportingDocumentSnapshot
import tech.justdev.application.document.ListExpenseSupportingDocumentsQuery
import tech.justdev.application.document.ListExpenseSupportingDocumentsResult
import tech.justdev.application.document.ListExpenseSupportingDocumentsUseCase
import tech.justdev.domain.document.valueobject.DocumentFileName
import tech.justdev.domain.document.valueobject.DocumentMediaType
import tech.justdev.domain.document.valueobject.DocumentSize
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.document.valueobject.SupportingDocumentAttachmentDeletion
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.UUID

class ExpenseSupportingDocumentControllerTest {
    private val authProvider = FakeAuthenticatedUserProvider()
    private val listSupportingDocumentsUseCase = FakeListExpenseSupportingDocumentsUseCase()
    private val controller =
        ExpenseSupportingDocumentController(
            authenticatedUserProvider = authProvider,
            listExpenseSupportingDocumentsUseCase = listSupportingDocumentsUseCase,
            configuration = SupportingDocumentDownloadConfiguration(),
        )

    @Nested
    inner class ListExpenseSupportingDocuments {
        @Test
        fun `should map current and historical documents including replacement deletion and download`() =
            runTest {
                val groupId = UUID.randomUUID()
                val expenseId = UUID.randomUUID()
                val sourceUploadIntent = UUID.randomUUID()
                val replacementSourceUploadIntent = UUID.randomUUID()
                val attachedAt = Instant.parse("2026-09-23T10:00:00Z")
                val deletedAt = Instant.parse("2026-09-23T11:00:00Z")
                val expiresAt = Instant.parse("2026-09-23T10:05:00Z")
                val current =
                    snapshot(
                        sourceUploadIntent = sourceUploadIntent,
                        attachedAt = attachedAt,
                        downloadExpiresAt = expiresAt,
                    )
                val historical =
                    snapshot(
                        sourceUploadIntent = replacementSourceUploadIntent,
                        attachedAt = attachedAt.minusSeconds(60),
                        replacesSourceUploadIntent = sourceUploadIntent,
                        deletion = SupportingDocumentAttachmentDeletion(MemberEmail.of("deleter@example.com"), deletedAt),
                        downloadExpiresAt = expiresAt.minusSeconds(60),
                    )
                listSupportingDocumentsUseCase.result =
                    ListExpenseSupportingDocumentsResult(
                        current = listOf(current),
                        history = listOf(historical, current),
                    )

                val response = controller.listExpenseSupportingDocuments(groupId, expenseId)

                assertEquals(
                    ExpenseSupportingDocumentsResponse(
                        current =
                            listOf(
                                ExpenseSupportingDocumentResponse(
                                    sourceUploadIntent = sourceUploadIntent,
                                    fileName = "invoice.pdf",
                                    mediaType = "application/pdf",
                                    sizeBytes = 1234,
                                    uploader = "uploader@example.com",
                                    attachedAt = attachedAt,
                                    replacesSourceUploadIntent = null,
                                    deletion = null,
                                    download =
                                        SupportingDocumentDownloadResponse(
                                            url = URI("https://storage.example.test/$sourceUploadIntent"),
                                            expiresAt = expiresAt,
                                        ),
                                ),
                            ),
                        history =
                            listOf(
                                ExpenseSupportingDocumentResponse(
                                    sourceUploadIntent = replacementSourceUploadIntent,
                                    fileName = "invoice.pdf",
                                    mediaType = "application/pdf",
                                    sizeBytes = 1234,
                                    uploader = "uploader@example.com",
                                    attachedAt = attachedAt.minusSeconds(60),
                                    replacesSourceUploadIntent = sourceUploadIntent,
                                    deletion = SupportingDocumentDeletionResponse("deleter@example.com", deletedAt),
                                    download =
                                        SupportingDocumentDownloadResponse(
                                            url = URI("https://storage.example.test/$replacementSourceUploadIntent"),
                                            expiresAt = expiresAt.minusSeconds(60),
                                        ),
                                ),
                                ExpenseSupportingDocumentResponse(
                                    sourceUploadIntent = sourceUploadIntent,
                                    fileName = "invoice.pdf",
                                    mediaType = "application/pdf",
                                    sizeBytes = 1234,
                                    uploader = "uploader@example.com",
                                    attachedAt = attachedAt,
                                    replacesSourceUploadIntent = null,
                                    deletion = null,
                                    download =
                                        SupportingDocumentDownloadResponse(
                                            url = URI("https://storage.example.test/$sourceUploadIntent"),
                                            expiresAt = expiresAt,
                                        ),
                                ),
                            ),
                    ),
                    response,
                )
            }

        @Test
        fun `should pass group expense authenticated member and default download validity to the use case`() =
            runTest {
                val groupId = UUID.randomUUID()
                val expenseId = UUID.randomUUID()

                controller.listExpenseSupportingDocuments(groupId, expenseId)

                assertEquals(
                    ListExpenseSupportingDocumentsQuery(
                        group = GroupId(groupId),
                        expense = ExpenseId(expenseId),
                        requestedBy = MemberEmail.of("member@example.com"),
                        downloadValidFor = Duration.ofMinutes(5),
                    ),
                    listSupportingDocumentsUseCase.lastQuery,
                )
            }

        @Test
        fun `should return empty current and history lists`() =
            runTest {
                listSupportingDocumentsUseCase.result = ListExpenseSupportingDocumentsResult(emptyList(), emptyList())

                val response = controller.listExpenseSupportingDocuments(UUID.randomUUID(), UUID.randomUUID())

                assertEquals(ExpenseSupportingDocumentsResponse(emptyList(), emptyList()), response)
            }

        @Test
        fun `should use the configured download validity duration`() =
            runTest {
                val configuredValidity = Duration.ofSeconds(42)
                val configuredController =
                    ExpenseSupportingDocumentController(
                        authenticatedUserProvider = authProvider,
                        listExpenseSupportingDocumentsUseCase = listSupportingDocumentsUseCase,
                        configuration = SupportingDocumentDownloadConfiguration().apply { validFor = configuredValidity },
                    )

                configuredController.listExpenseSupportingDocuments(UUID.randomUUID(), UUID.randomUUID())

                assertEquals(configuredValidity, requireNotNull(listSupportingDocumentsUseCase.lastQuery).downloadValidFor)
            }
    }

    private fun snapshot(
        sourceUploadIntent: UUID,
        attachedAt: Instant,
        replacesSourceUploadIntent: UUID? = null,
        deletion: SupportingDocumentAttachmentDeletion? = null,
        downloadExpiresAt: Instant,
    ): ExpenseSupportingDocumentSnapshot =
        ExpenseSupportingDocumentSnapshot(
            sourceUploadIntent = DocumentUploadIntentId(sourceUploadIntent),
            fileName = DocumentFileName.of("invoice.pdf"),
            mediaType = DocumentMediaType.of("application/pdf"),
            size = DocumentSize.ofBytes(1234),
            uploader = MemberEmail.of("uploader@example.com"),
            attachedAt = attachedAt,
            replacesSourceUploadIntent = replacesSourceUploadIntent?.let(::DocumentUploadIntentId),
            deletion = deletion,
            download =
                DocumentDownloadTarget(
                    uri = URI("https://storage.example.test/$sourceUploadIntent"),
                    expiresAt = downloadExpiresAt,
                ),
        )

    private class FakeAuthenticatedUserProvider : AuthenticatedUserProvider {
        override suspend fun currentAuthenticatedUser(): AuthenticatedUser = AuthenticatedUser(MemberEmail.of("member@example.com"))
    }

    private class FakeListExpenseSupportingDocumentsUseCase : ListExpenseSupportingDocumentsUseCase {
        var lastQuery: ListExpenseSupportingDocumentsQuery? = null
        var result = ListExpenseSupportingDocumentsResult(emptyList(), emptyList())

        override suspend fun invoke(query: ListExpenseSupportingDocumentsQuery): ListExpenseSupportingDocumentsResult {
            lastQuery = query
            return result
        }
    }
}
