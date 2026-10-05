package tech.justdev.interfaces.reimbursement

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.application.auth.AuthenticatedUser
import tech.justdev.application.auth.AuthenticatedUserProvider
import tech.justdev.application.document.DocumentDownloadRequest
import tech.justdev.application.document.DocumentDownloadTarget
import tech.justdev.application.document.DocumentStorage
import tech.justdev.application.document.DocumentUploadRequest
import tech.justdev.application.document.DocumentUploadTarget
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
import tech.justdev.domain.document.entity.DocumentMetadata
import tech.justdev.domain.document.valueobject.DocumentFileName
import tech.justdev.domain.document.valueobject.DocumentMediaType
import tech.justdev.domain.document.valueobject.DocumentSha256
import tech.justdev.domain.document.valueobject.DocumentSize
import tech.justdev.domain.document.valueobject.DocumentStorageKey
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.reimbursement.entity.Reimbursement
import tech.justdev.domain.reimbursement.entity.ReimbursementSupportingDocument
import tech.justdev.domain.reimbursement.valueobject.ReimbursementId
import tech.justdev.domain.reimbursement.valueobject.ReimbursementRejectionReason
import tech.justdev.domain.reimbursement.valueobject.ReimbursementStatus
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.interfaces.configuration.SupportingDocumentDownloadConfiguration
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

class ReimbursementControllerTest {
    private val listUseCase = ListUseCaseFake()
    private val getUseCase = GetUseCaseFake()
    private val directUseCase = DirectUseCaseFake()
    private val documentedUseCase = DocumentedUseCaseFake()
    private val acceptUseCase = AcceptUseCaseFake()
    private val rejectUseCase = RejectUseCaseFake()
    private val authenticatedUserProvider = AuthenticatedUserProviderFake()
    private val configuration = SupportingDocumentDownloadConfiguration().apply { validFor = Duration.ofMinutes(7) }
    private val documentStorage = DocumentStorageFake()
    private val presenter = ReimbursementPresenter(documentStorage, configuration)
    private val controller =
        ReimbursementController(
            authenticatedUserProvider,
            listUseCase,
            getUseCase,
            directUseCase,
            documentedUseCase,
            acceptUseCase,
            rejectUseCase,
            presenter,
        )

    @Nested
    inner class ListReimbursements {
        @Test
        fun `should present complete reimbursements without exposing storage internals`() =
            runTest {
                listUseCase.result = listOf(reimbursement())

                val response = controller.listReimbursements(GROUP).single()

                assertEquals(
                    ReimbursementResponse(
                        REIMBURSEMENT,
                        "payer@example.com",
                        "receiver@example.com",
                        1_200,
                        REIMBURSED_AT,
                        "payer@example.com",
                        DECLARED_AT,
                        ReimbursementStatusResponse.REJECTED,
                        DECIDED_AT,
                        "Duplicate",
                        listOf(
                            ReimbursementSupportingDocumentResponse(
                                DOCUMENT,
                                "receipt.pdf",
                                "application/pdf",
                                256,
                                "payer@example.com",
                                ATTACHED_AT,
                                ReimbursementDocumentDownloadResponse(URI.create("https://documents.example/receipt.pdf"), EXPIRES_AT),
                            ),
                        ),
                    ),
                    response,
                )
                assertEquals(GROUP, listUseCase.query!!.group.toPrimitive())
                assertEquals(REQUESTER, listUseCase.query!!.requestedBy.toPrimitive())
                assertEquals(
                    listOf(
                        DocumentDownloadRequest(
                            DocumentStorageKey.of("documents/$DOCUMENT"),
                            DocumentFileName.of("receipt.pdf"),
                            Duration.ofMinutes(7),
                        ),
                    ),
                    documentStorage.downloadRequests,
                )
            }
    }

    @Nested
    inner class GetReimbursement {
        @Test
        fun `should pass group reimbursement and authenticated member`() =
            runTest {
                getUseCase.result = reimbursement()

                controller.getReimbursement(GROUP, REIMBURSEMENT)

                assertEquals(
                    GetReimbursementQuery(
                        tech.justdev.domain.shared.valueobject
                            .GroupId(GROUP),
                        ReimbursementId(REIMBURSEMENT),
                        MemberEmail.of(REQUESTER),
                    ),
                    getUseCase.query,
                )
            }
    }

    @Nested
    inner class RecordDirectReimbursement {
        @Test
        fun `should map a direct request and record it on behalf of the authenticated receiver`() =
            runTest {
                val before = Instant.now()
                controller.recordDirectReimbursement(GROUP, DirectReimbursementRequest(" PAYER@EXAMPLE.COM ", 1_200, REIMBURSED_AT))
                val after = Instant.now()

                val command = requireNotNull(directUseCase.command)
                assertEquals(GROUP, command.group.toPrimitive())
                assertEquals("payer@example.com", command.paidBy.toPrimitive())
                assertEquals("receiver@example.com", command.recordedBy.toPrimitive())
                assertEquals(1_200, command.amountInCents)
                assertEquals(REIMBURSED_AT, command.reimbursedAt)
                assertTrue(command.recordedAt in before..after)
            }

        @Test
        fun `should reject an invalid payer email before calling the use case`() {
            assertThrows<IllegalArgumentException> {
                runTest { controller.recordDirectReimbursement(GROUP, DirectReimbursementRequest("not-an-email", 1_200, REIMBURSED_AT)) }
            }

            assertEquals(null, directUseCase.command)
        }
    }

    @Nested
    inner class DeclareDocumentedReimbursement {
        @Test
        fun `should map upload intents and declare on behalf of the authenticated payer`() =
            runTest {
                val first = UUID.randomUUID()
                val second = UUID.randomUUID()
                authenticatedUserProvider.email = "payer@example.com"
                val before = Instant.now()

                controller.declareDocumentedReimbursement(
                    GROUP,
                    DocumentedReimbursementRequest(" RECEIVER@EXAMPLE.COM ", 1_200, REIMBURSED_AT, setOf(first, second)),
                )
                val after = Instant.now()

                val command = requireNotNull(documentedUseCase.command)
                assertEquals(GROUP, command.group.toPrimitive())
                assertEquals("payer@example.com", command.paidBy.toPrimitive())
                assertEquals("receiver@example.com", command.receivedBy.toPrimitive())
                assertEquals(1_200, command.amountInCents)
                assertEquals(REIMBURSED_AT, command.reimbursedAt)
                assertTrue(command.declaredAt in before..after)
                assertEquals(setOf(DocumentUploadIntentId(first), DocumentUploadIntentId(second)), command.supportingDocumentUploadIntents)
            }

        @Test
        fun `should reject an invalid receiver email before calling the use case`() {
            assertThrows<IllegalArgumentException> {
                runTest {
                    controller.declareDocumentedReimbursement(
                        GROUP,
                        DocumentedReimbursementRequest("not-an-email", 1_200, REIMBURSED_AT, setOf(UUID.randomUUID())),
                    )
                }
            }

            assertEquals(null, documentedUseCase.command)
        }
    }

    @Nested
    inner class AcceptReimbursement {
        @Test
        fun `should map the authenticated reviewer to a complete acceptance command`() =
            runTest {
                val before = Instant.now()
                controller.acceptReimbursement(GROUP, REIMBURSEMENT)
                val after = Instant.now()

                val accepted = requireNotNull(acceptUseCase.command)
                assertTrue(accepted.acceptedAt in before..after)
                assertEquals(GROUP, accepted.group.toPrimitive())
                assertEquals(REIMBURSEMENT, accepted.reimbursement.toPrimitive())
                assertEquals("receiver@example.com", accepted.acceptedBy.toPrimitive())
            }
    }

    @Nested
    inner class RejectReimbursement {
        @Test
        fun `should map a complete rejection command and normalize a blank reason to absence`() =
            runTest {
                val before = Instant.now()
                controller.rejectReimbursement(GROUP, REIMBURSEMENT, ReimbursementRejectionRequest("  "))
                val after = Instant.now()

                val rejected = requireNotNull(rejectUseCase.command)
                assertEquals(GROUP, rejected.group.toPrimitive())
                assertEquals(REIMBURSEMENT, rejected.reimbursement.toPrimitive())
                assertEquals("receiver@example.com", rejected.rejectedBy.toPrimitive())
                assertTrue(rejected.rejectedAt in before..after)
                assertEquals(null, rejected.reason)
            }

        @Test
        fun `should map a non-blank rejection reason`() =
            runTest {
                controller.rejectReimbursement(GROUP, REIMBURSEMENT, ReimbursementRejectionRequest("Duplicate"))

                assertEquals(ReimbursementRejectionReason.of("Duplicate"), rejectUseCase.command!!.reason)
            }
    }

    private fun reimbursement() =
        Reimbursement.restore(
            ReimbursementId(REIMBURSEMENT),
            GroupId(GROUP),
            MemberEmail.of("payer@example.com"),
            MemberEmail.of("receiver@example.com"),
            MoneyAmount.ofCents(1_200),
            REIMBURSED_AT,
            MemberEmail.of("payer@example.com"),
            DECLARED_AT,
            ReimbursementStatus.Rejected(DECIDED_AT, ReimbursementRejectionReason.of("Duplicate")),
            listOf(
                ReimbursementSupportingDocument.restore(
                    DocumentUploadIntentId(DOCUMENT),
                    GroupId(GROUP),
                    MemberEmail.of("payer@example.com"),
                    DocumentStorageKey.of("documents/$DOCUMENT"),
                    DocumentFileName.of("receipt.pdf"),
                    DocumentMetadata(
                        DocumentMediaType.of("application/pdf"),
                        DocumentSize.ofBytes(256),
                        CHECKSUM,
                    ),
                    ATTACHED_AT,
                ),
            ),
        )

    private class AuthenticatedUserProviderFake : AuthenticatedUserProvider {
        var email = REQUESTER

        override suspend fun currentAuthenticatedUser() = AuthenticatedUser(MemberEmail.of(email))
    }

    private class ListUseCaseFake : ListReimbursementsUseCase {
        var query: ListReimbursementsQuery? = null
        var result = emptyList<Reimbursement>()

        override suspend fun invoke(query: ListReimbursementsQuery): List<Reimbursement> = result.also { this.query = query }
    }

    private class GetUseCaseFake : GetReimbursementUseCase {
        var query: GetReimbursementQuery? = null
        lateinit var result: Reimbursement

        override suspend fun invoke(query: GetReimbursementQuery): Reimbursement = result.also { this.query = query }
    }

    private class DocumentStorageFake : DocumentStorage {
        val downloadRequests = mutableListOf<DocumentDownloadRequest>()

        override suspend fun presignUpload(request: DocumentUploadRequest): DocumentUploadTarget = error("not used")

        override suspend fun inspect(key: DocumentStorageKey): DocumentMetadata? = error("not used")

        override suspend fun presignDownload(request: DocumentDownloadRequest): DocumentDownloadTarget =
            DocumentDownloadTarget(URI.create("https://documents.example/receipt.pdf"), EXPIRES_AT).also {
                downloadRequests += request
            }
    }

    private class DirectUseCaseFake : RecordDirectReimbursementUseCase {
        var command: RecordDirectReimbursementCommand? = null

        override suspend fun invoke(command: RecordDirectReimbursementCommand) {
            this.command = command
        }
    }

    private class DocumentedUseCaseFake : DeclareDocumentedReimbursementUseCase {
        var command: DeclareDocumentedReimbursementCommand? = null

        override suspend fun invoke(command: DeclareDocumentedReimbursementCommand) {
            this.command = command
        }
    }

    private class AcceptUseCaseFake : AcceptReimbursementUseCase {
        var command: AcceptReimbursementCommand? = null

        override suspend fun invoke(command: AcceptReimbursementCommand) {
            this.command = command
        }
    }

    private class RejectUseCaseFake : RejectReimbursementUseCase {
        var command: RejectReimbursementCommand? = null

        override suspend fun invoke(command: RejectReimbursementCommand) {
            this.command = command
        }
    }

    private companion object {
        val GROUP: UUID = UUID.randomUUID()
        val REIMBURSEMENT: UUID = UUID.randomUUID()
        val DOCUMENT: UUID = UUID.randomUUID()
        const val REQUESTER = "receiver@example.com"
        val REIMBURSED_AT = Instant.parse("2026-10-02T09:00:00Z")
        val DECLARED_AT = Instant.parse("2026-10-02T10:00:00Z")
        val DECIDED_AT = Instant.parse("2026-10-02T11:00:00Z")
        val ATTACHED_AT = Instant.parse("2026-10-02T10:00:01Z")
        val EXPIRES_AT = Instant.parse("2026-10-02T10:05:00Z")
        val CHECKSUM = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32)))
    }
}
