package tech.justdev.interfaces.document

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tech.justdev.application.auth.AuthenticatedUser
import tech.justdev.application.auth.AuthenticatedUserProvider
import tech.justdev.application.document.DocumentDownloadTarget
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
    private val listSupportingDocumentAuditTrailUseCase = FakeListExpenseSupportingDocumentAuditTrailUseCase()
    private val replaceSupportingDocumentUseCase = FakeReplaceExpenseSupportingDocumentUseCase()
    private val deleteSupportingDocumentUseCase = FakeDeleteExpenseSupportingDocumentUseCase()
    private val controller =
        ExpenseSupportingDocumentController(
            authenticatedUserProvider = authProvider,
            listExpenseSupportingDocumentsUseCase = listSupportingDocumentsUseCase,
            listExpenseSupportingDocumentAuditTrailUseCase = listSupportingDocumentAuditTrailUseCase,
            replaceExpenseSupportingDocumentUseCase = replaceSupportingDocumentUseCase,
            deleteExpenseSupportingDocumentUseCase = deleteSupportingDocumentUseCase,
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
                        canDelete = true,
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
                                    canDelete = true,
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
                                    canDelete = false,
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
                                    canDelete = true,
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
                        listExpenseSupportingDocumentAuditTrailUseCase = listSupportingDocumentAuditTrailUseCase,
                        replaceExpenseSupportingDocumentUseCase = replaceSupportingDocumentUseCase,
                        deleteExpenseSupportingDocumentUseCase = deleteSupportingDocumentUseCase,
                        configuration = SupportingDocumentDownloadConfiguration().apply { validFor = configuredValidity },
                    )

                configuredController.listExpenseSupportingDocuments(UUID.randomUUID(), UUID.randomUUID())

                assertEquals(configuredValidity, requireNotNull(listSupportingDocumentsUseCase.lastQuery).downloadValidFor)
            }
    }

    @Nested
    inner class ListExpenseSupportingDocumentAuditTrail {
        @Test
        fun `should map every audit action`() =
            runTest {
                val attachedUploadIntent = UUID.randomUUID()
                val replacedUploadIntent = UUID.randomUUID()
                val replacementUploadIntent = UUID.randomUUID()
                val deletedUploadIntent = UUID.randomUUID()
                val attachedAt = Instant.parse("2026-09-23T10:00:00Z")
                val replacedAt = Instant.parse("2026-09-23T11:00:00Z")
                val deletedAt = Instant.parse("2026-09-23T12:00:00Z")
                listSupportingDocumentAuditTrailUseCase.result =
                    listOf(
                        auditEntry(ExpenseSupportingDocumentAuditAction.ATTACHED, attachedAt, attachedUploadIntent),
                        auditEntry(
                            action = ExpenseSupportingDocumentAuditAction.REPLACED,
                            occurredAt = replacedAt,
                            documentUploadIntent = replacementUploadIntent,
                            replacedDocumentUploadIntent = replacedUploadIntent,
                        ),
                        auditEntry(ExpenseSupportingDocumentAuditAction.DELETED, deletedAt, deletedUploadIntent),
                    )

                val response = controller.listExpenseSupportingDocumentAuditTrail(UUID.randomUUID(), UUID.randomUUID())

                assertEquals(
                    listOf(
                        ExpenseSupportingDocumentAuditEntryResponse(
                            action = ExpenseSupportingDocumentAuditActionResponse.ATTACHED,
                            occurredAt = attachedAt,
                            performedBy = "uploader@example.com",
                            documentUploadIntent = attachedUploadIntent,
                            replacedDocumentUploadIntent = null,
                            fileName = "invoice.pdf",
                        ),
                        ExpenseSupportingDocumentAuditEntryResponse(
                            action = ExpenseSupportingDocumentAuditActionResponse.REPLACED,
                            occurredAt = replacedAt,
                            performedBy = "uploader@example.com",
                            documentUploadIntent = replacementUploadIntent,
                            replacedDocumentUploadIntent = replacedUploadIntent,
                            fileName = "invoice.pdf",
                        ),
                        ExpenseSupportingDocumentAuditEntryResponse(
                            action = ExpenseSupportingDocumentAuditActionResponse.DELETED,
                            occurredAt = deletedAt,
                            performedBy = "uploader@example.com",
                            documentUploadIntent = deletedUploadIntent,
                            replacedDocumentUploadIntent = null,
                            fileName = "invoice.pdf",
                        ),
                    ),
                    response,
                )
            }

        @Test
        fun `should pass group expense and authenticated member to the use case`() =
            runTest {
                val groupId = UUID.randomUUID()
                val expenseId = UUID.randomUUID()

                controller.listExpenseSupportingDocumentAuditTrail(groupId, expenseId)

                assertEquals(
                    ListExpenseSupportingDocumentAuditTrailQuery(
                        group = GroupId(groupId),
                        expense = ExpenseId(expenseId),
                        requestedBy = MemberEmail.of("member@example.com"),
                    ),
                    listSupportingDocumentAuditTrailUseCase.lastQuery,
                )
            }

        @Test
        fun `should return an empty audit trail`() =
            runTest {
                listSupportingDocumentAuditTrailUseCase.result = emptyList()

                val response = controller.listExpenseSupportingDocumentAuditTrail(UUID.randomUUID(), UUID.randomUUID())

                assertEquals(emptyList<ExpenseSupportingDocumentAuditEntryResponse>(), response)
            }
    }

    @Nested
    inner class ReplaceExpenseSupportingDocument {
        @Test
        fun `should map source replacement authenticated member and current time to the replacement command`() =
            runTest {
                val groupId = UUID.randomUUID()
                val expenseId = UUID.randomUUID()
                val sourceUploadIntent = UUID.randomUUID()
                val replacementUploadIntent = UUID.randomUUID()
                val before = Instant.now()

                val result =
                    controller.replaceExpenseSupportingDocument(
                        groupId = groupId,
                        expenseId = expenseId,
                        sourceUploadIntent = sourceUploadIntent,
                        request = ReplaceExpenseSupportingDocumentRequest(replacementUploadIntent),
                    )

                val after = Instant.now()
                val command = requireNotNull(replaceSupportingDocumentUseCase.lastCommand)
                assertEquals(Unit, result)
                assertEquals(GroupId(groupId), command.group)
                assertEquals(ExpenseId(expenseId), command.expense)
                assertEquals(DocumentUploadIntentId(sourceUploadIntent), command.replacedSourceUploadIntent)
                assertEquals(DocumentUploadIntentId(replacementUploadIntent), command.replacementUploadIntent)
                assertEquals(MemberEmail.of("member@example.com"), command.requestedBy)
                assertTrue(!command.replacedAt.isBefore(before))
                assertTrue(!command.replacedAt.isAfter(after))
            }
    }

    @Nested
    inner class DeleteExpenseSupportingDocument {
        @Test
        fun `should map source authenticated member and current time to the deletion command`() =
            runTest {
                val groupId = UUID.randomUUID()
                val expenseId = UUID.randomUUID()
                val sourceUploadIntent = UUID.randomUUID()
                val before = Instant.now()

                val result =
                    controller.deleteExpenseSupportingDocument(
                        groupId = groupId,
                        expenseId = expenseId,
                        sourceUploadIntent = sourceUploadIntent,
                    )

                val after = Instant.now()
                val command = requireNotNull(deleteSupportingDocumentUseCase.lastCommand)
                assertEquals(Unit, result)
                assertEquals(GroupId(groupId), command.group)
                assertEquals(ExpenseId(expenseId), command.expense)
                assertEquals(DocumentUploadIntentId(sourceUploadIntent), command.sourceUploadIntent)
                assertEquals(MemberEmail.of("member@example.com"), command.requestedBy)
                assertTrue(!command.deletedAt.isBefore(before))
                assertTrue(!command.deletedAt.isAfter(after))
            }
    }

    private fun snapshot(
        sourceUploadIntent: UUID,
        attachedAt: Instant,
        replacesSourceUploadIntent: UUID? = null,
        deletion: SupportingDocumentAttachmentDeletion? = null,
        canDelete: Boolean = false,
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
            canDelete = canDelete,
            download =
                DocumentDownloadTarget(
                    uri = URI("https://storage.example.test/$sourceUploadIntent"),
                    expiresAt = downloadExpiresAt,
                ),
        )

    private fun auditEntry(
        action: ExpenseSupportingDocumentAuditAction,
        occurredAt: Instant,
        documentUploadIntent: UUID,
        replacedDocumentUploadIntent: UUID? = null,
    ): ExpenseSupportingDocumentAuditEntrySnapshot =
        ExpenseSupportingDocumentAuditEntrySnapshot(
            action = action,
            occurredAt = occurredAt,
            performedBy = MemberEmail.of("uploader@example.com"),
            documentUploadIntent = DocumentUploadIntentId(documentUploadIntent),
            replacedDocumentUploadIntent = replacedDocumentUploadIntent?.let(::DocumentUploadIntentId),
            fileName = DocumentFileName.of("invoice.pdf"),
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

    private class FakeListExpenseSupportingDocumentAuditTrailUseCase : ListExpenseSupportingDocumentAuditTrailUseCase {
        var lastQuery: ListExpenseSupportingDocumentAuditTrailQuery? = null
        var result: List<ExpenseSupportingDocumentAuditEntrySnapshot> = emptyList()

        override suspend fun invoke(
            query: ListExpenseSupportingDocumentAuditTrailQuery,
        ): List<ExpenseSupportingDocumentAuditEntrySnapshot> {
            lastQuery = query
            return result
        }
    }

    private class FakeReplaceExpenseSupportingDocumentUseCase : ReplaceExpenseSupportingDocumentUseCase {
        var lastCommand: ReplaceExpenseSupportingDocumentCommand? = null

        override suspend fun invoke(command: ReplaceExpenseSupportingDocumentCommand) {
            lastCommand = command
        }
    }

    private class FakeDeleteExpenseSupportingDocumentUseCase : DeleteExpenseSupportingDocumentUseCase {
        var lastCommand: DeleteExpenseSupportingDocumentCommand? = null

        override suspend fun invoke(command: DeleteExpenseSupportingDocumentCommand) {
            lastCommand = command
        }
    }
}
