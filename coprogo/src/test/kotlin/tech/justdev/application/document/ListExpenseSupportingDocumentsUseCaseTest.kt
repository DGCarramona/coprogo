package tech.justdev.application.document

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.application.expense.ExpenseNotFoundException
import tech.justdev.application.group.GroupAccessDeniedException
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.domain.document.entity.DocumentMetadata
import tech.justdev.domain.document.entity.DocumentUploadIntent
import tech.justdev.domain.document.entity.DocumentUploadIntentStatus
import tech.justdev.domain.document.entity.ExpenseSupportingDocument
import tech.justdev.domain.document.entity.ExpenseSupportingDocuments
import tech.justdev.domain.document.valueobject.DocumentFileName
import tech.justdev.domain.document.valueobject.DocumentMediaType
import tech.justdev.domain.document.valueobject.DocumentSha256
import tech.justdev.domain.document.valueobject.DocumentSize
import tech.justdev.domain.document.valueobject.DocumentStorageKey
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.document.valueobject.SupportingDocumentAttachmentDeletion
import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.repository.ExpenseRepository
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.expense.valueobject.ExpenseShare
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.testsupport.expenseId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.testUuid
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.Base64

class ListExpenseSupportingDocumentsUseCaseTest {
    @Nested
    inner class Invoke {
        @Test
        fun `should verify membership before accessing any document dependency`() {
            val interactions = mutableListOf<String>()

            assertThrows<GroupAccessDeniedException> {
                runTest {
                    useCase(
                        groupRepository = RecordingGroupRepository(interactions, group()),
                        expenseRepository = FailingExpenseRepository(),
                        documentStorage = FailingDocumentStorage(),
                    )(query(requestedBy = OUTSIDER))
                }
            }

            assertEquals(listOf("membership"), interactions)
        }

        @Test
        fun `should reject an absent expense before reading document history`() {
            val interactions = mutableListOf<String>()
            val expenseRepository = RecordingExpenseRepository(interactions, expense = null)

            val error =
                assertThrows<ExpenseNotFoundException> {
                    runTest {
                        useCase(
                            groupRepository = RecordingGroupRepository(interactions, group()),
                            expenseRepository = expenseRepository,
                            documentStorage = FailingDocumentStorage(),
                        )(query())
                    }
                }

            assertEquals(EXPENSE, error.id)
            assertEquals(GROUP, error.group)
            assertEquals(listOf("membership", "expense"), interactions)
            assertEquals(listOf(ExpenseLookup(EXPENSE, GROUP)), expenseRepository.lookups)
        }

        @Test
        fun `should return empty current and history without presigning downloads`() =
            runTest {
                val interactions = mutableListOf<String>()
                val documentStorage = RecordingDocumentStorage(interactions)

                val result =
                    useCase(
                        groupRepository = RecordingGroupRepository(interactions, group()),
                        expenseRepository = RecordingExpenseRepository(interactions, expense()),
                        documentStorage = documentStorage,
                    )(query())

                assertEquals(ListExpenseSupportingDocumentsResult(emptyList(), emptyList()), result)
                assertEquals(emptyList<DocumentDownloadRequest>(), documentStorage.downloadRequests)
                assertEquals(listOf("membership", "expense"), interactions)
            }

        @Test
        fun `should return current and complete history with replacement deletion and exact mapping`() =
            runTest {
                val originalIntent = consumedIntent(ORIGINAL, "original.pdf", "application/pdf", 512, ORIGINAL_ATTACHED_AT)
                val original = document(originalIntent)
                val replacementIntent = consumedIntent(REPLACEMENT, "replacement.jpg", "image/jpeg", 1_024, REPLACEMENT_ATTACHED_AT)
                val replacement =
                    document(
                        replacementIntent,
                        replaces = ORIGINAL,
                        deletion = SupportingDocumentAttachmentDeletion(UPLOADER, DELETED_AT),
                    )
                val history = listOf(original, replacement)
                val documentStorage = RecordingDocumentStorage(mutableListOf())

                val result =
                    useCase(
                        groupRepository = RecordingGroupRepository(mutableListOf(), group()),
                        expenseRepository = RecordingExpenseRepository(mutableListOf(), expense(history)),
                        documentStorage = documentStorage,
                    )(query())

                assertEquals(
                    ListExpenseSupportingDocumentsResult(
                        current = emptyList(),
                        history =
                            listOf(
                                snapshot(
                                    document = original,
                                    download = downloadTarget(ORIGINAL),
                                ),
                                snapshot(
                                    document = replacement,
                                    download = downloadTarget(REPLACEMENT),
                                ),
                            ),
                    ),
                    result,
                )
            }

        @Test
        fun `should presign each hydrated document once`() =
            runTest {
                val original = document(consumedIntent(ORIGINAL, "original.pdf", "application/pdf", 512, ORIGINAL_ATTACHED_AT))
                val replacement =
                    document(
                        consumedIntent(REPLACEMENT, "replacement.jpg", "image/jpeg", 1_024, REPLACEMENT_ATTACHED_AT),
                        replaces = ORIGINAL,
                    )
                val duplicateCurrent = document(consumedIntent(CURRENT, "current.png", "image/png", 256, CURRENT_ATTACHED_AT))
                val history = listOf(original, replacement, duplicateCurrent)
                val documentStorage = RecordingDocumentStorage(mutableListOf())

                useCase(
                    groupRepository = RecordingGroupRepository(mutableListOf(), group()),
                    expenseRepository = RecordingExpenseRepository(mutableListOf(), expense(history)),
                    documentStorage = documentStorage,
                )(query())

                assertEquals(
                    listOf(
                        DocumentDownloadRequest(
                            key = DocumentStorageKey.of("documents/$ORIGINAL"),
                            fileName = DocumentFileName.of("original.pdf"),
                            validFor = DOWNLOAD_VALID_FOR,
                        ),
                        DocumentDownloadRequest(
                            key = DocumentStorageKey.of("documents/$REPLACEMENT"),
                            fileName = DocumentFileName.of("replacement.jpg"),
                            validFor = DOWNLOAD_VALID_FOR,
                        ),
                        DocumentDownloadRequest(
                            key = DocumentStorageKey.of("documents/$CURRENT"),
                            fileName = DocumentFileName.of("current.png"),
                            validFor = DOWNLOAD_VALID_FOR,
                        ),
                    ),
                    documentStorage.downloadRequests,
                )
            }
    }

    private fun useCase(
        groupRepository: GroupRepository,
        expenseRepository: ExpenseRepository,
        documentStorage: DocumentStorage,
    ): ListExpenseSupportingDocumentsUseCase =
        ListExpenseSupportingDocumentsUseCaseImpl(
            groupAccessPolicy = GroupAccessPolicy(groupRepository),
            expenseRepository = expenseRepository,
            documentStorage = documentStorage,
        )

    private fun query(requestedBy: MemberEmail = UPLOADER): ListExpenseSupportingDocumentsQuery =
        ListExpenseSupportingDocumentsQuery(
            group = GROUP,
            expense = EXPENSE,
            requestedBy = requestedBy,
            downloadValidFor = DOWNLOAD_VALID_FOR,
        )

    private fun group(): Group = Group.create(GROUP, UPLOADER, CREATED_AT).addMember(OUTSIDER_MEMBER, CREATED_AT.plusSeconds(1))

    private fun expense(documents: List<ExpenseSupportingDocument> = emptyList()): Expense =
        Expense
            .propose(
                id = EXPENSE,
                group = GROUP,
                title = "Documented repair",
                createdBy = UPLOADER,
                totalAmount = MoneyAmount.ofCents(100),
                createdAt = CREATED_AT,
                shares = setOf(ExpenseShare(UPLOADER, MoneyAmount.ofCents(50)), ExpenseShare(OUTSIDER_MEMBER, MoneyAmount.ofCents(50))),
            ).copy(supportingDocuments = ExpenseSupportingDocuments.restore(documents))

    private fun document(
        intent: DocumentUploadIntent,
        replaces: DocumentUploadIntentId? = null,
        deletion: SupportingDocumentAttachmentDeletion? = null,
    ): ExpenseSupportingDocument =
        ExpenseSupportingDocument.restore(
            sourceUploadIntent = intent.id,
            uploader = intent.uploader,
            storageKey = intent.storageKey,
            fileName = intent.fileName,
            metadata = intent.expectedMetadata,
            attachedAt = (intent.status as DocumentUploadIntentStatus.Consumed).consumedAt,
            replacesSourceUploadIntent = replaces,
            deletion = deletion,
        )

    private fun consumedIntent(
        id: DocumentUploadIntentId,
        fileName: String,
        mediaType: String,
        size: Long,
        consumedAt: Instant,
    ): DocumentUploadIntent =
        DocumentUploadIntent.restore(
            id = id,
            group = GROUP,
            uploader = UPLOADER,
            storageKey = DocumentStorageKey.of("documents/$id"),
            fileName = DocumentFileName.of(fileName),
            expectedMetadata =
                DocumentMetadata(
                    mediaType = DocumentMediaType.of(mediaType),
                    size = DocumentSize.ofBytes(size),
                    checksum = CHECKSUM,
                ),
            createdAt = CREATED_AT,
            expiresAt = EXPIRES_AT,
            status = DocumentUploadIntentStatus.Consumed(VERIFIED_AT, consumedAt),
        )

    private fun snapshot(
        document: ExpenseSupportingDocument,
        download: DocumentDownloadTarget,
    ): ExpenseSupportingDocumentSnapshot =
        ExpenseSupportingDocumentSnapshot(
            sourceUploadIntent = document.sourceUploadIntent,
            fileName = document.fileName,
            mediaType = document.metadata.mediaType,
            size = document.metadata.size,
            uploader = document.uploader,
            attachedAt = document.attachedAt,
            replacesSourceUploadIntent = document.replacesSourceUploadIntent,
            deletion = document.deletion,
            download = download,
        )

    private fun downloadTarget(id: DocumentUploadIntentId): DocumentDownloadTarget =
        DocumentDownloadTarget(
            uri = URI.create("https://documents.example/$id"),
            expiresAt = DOWNLOAD_EXPIRES_AT,
        )

    private class RecordingGroupRepository(
        private val interactions: MutableList<String>,
        private val group: Group?,
    ) : GroupRepository {
        override suspend fun findById(id: GroupId): Group? {
            interactions += "membership"
            return group?.takeIf { it.id == id }
        }

        override suspend fun persist(group: Group) = Unit
    }

    private class RecordingExpenseRepository(
        private val interactions: MutableList<String>,
        private val expense: Expense?,
    ) : ExpenseRepository {
        val lookups = mutableListOf<ExpenseLookup>()

        override suspend fun findByIdAndGroup(
            id: ExpenseId,
            group: GroupId,
        ): Expense? {
            interactions += "expense"
            lookups += ExpenseLookup(id, group)
            return expense?.takeIf { it.id == id && it.group == group }
        }

        override suspend fun findByGroup(group: GroupId): List<Expense> = error("not used")

        override suspend fun findProposedByIdAndGroup(
            id: ExpenseId,
            group: GroupId,
        ): Expense? = error("not used")

        override suspend fun persist(expense: Expense) = error("not used")
    }

    private class RecordingDocumentStorage(
        private val interactions: MutableList<String>,
    ) : DocumentStorage {
        val downloadRequests = mutableListOf<DocumentDownloadRequest>()

        override suspend fun presignUpload(request: DocumentUploadRequest): DocumentUploadTarget = error("not used")

        override suspend fun inspect(key: DocumentStorageKey): DocumentMetadata? = error("not used")

        override suspend fun presignDownload(request: DocumentDownloadRequest): DocumentDownloadTarget {
            interactions += "download"
            downloadRequests += request
            return downloadTargetFor(request.key)
        }

        private fun downloadTargetFor(key: DocumentStorageKey): DocumentDownloadTarget =
            DocumentDownloadTarget(
                URI.create("https://documents.example/${key.toPrimitive().removePrefix("documents/")}"),
                DOWNLOAD_EXPIRES_AT,
            )
    }

    private class FailingExpenseRepository : ExpenseRepository {
        override suspend fun findByIdAndGroup(
            id: ExpenseId,
            group: GroupId,
        ): Expense? = error("expense repository must not be accessed")

        override suspend fun findByGroup(group: GroupId): List<Expense> = error("expense repository must not be accessed")

        override suspend fun findProposedByIdAndGroup(
            id: ExpenseId,
            group: GroupId,
        ): Expense? = error("expense repository must not be accessed")

        override suspend fun persist(expense: Expense) = error("expense repository must not be accessed")
    }

    private class FailingDocumentStorage : DocumentStorage {
        override suspend fun presignUpload(request: DocumentUploadRequest): DocumentUploadTarget = error("storage must not be accessed")

        override suspend fun inspect(key: DocumentStorageKey): DocumentMetadata? = error("storage must not be accessed")

        override suspend fun presignDownload(request: DocumentDownloadRequest): DocumentDownloadTarget =
            error("storage must not be accessed")
    }

    private data class ExpenseLookup(
        val expense: ExpenseId,
        val group: GroupId,
    )

    private companion object {
        val GROUP: GroupId = groupId("list-expense-documents-group")
        val EXPENSE: ExpenseId = expenseId("list-expense-documents-expense")
        val UPLOADER: MemberEmail = memberEmail("alice")
        val OUTSIDER_MEMBER: MemberEmail = memberEmail("bob")
        val OUTSIDER: MemberEmail = memberEmail("outsider")
        val ORIGINAL: DocumentUploadIntentId = DocumentUploadIntentId(testUuid("history-original"))
        val REPLACEMENT: DocumentUploadIntentId = DocumentUploadIntentId(testUuid("history-replace"))
        val CURRENT: DocumentUploadIntentId = DocumentUploadIntentId(testUuid("history-current"))
        val CREATED_AT: Instant = Instant.parse("2026-09-20T10:00:00Z")
        val VERIFIED_AT: Instant = Instant.parse("2026-09-20T10:01:00Z")
        val ORIGINAL_ATTACHED_AT: Instant = Instant.parse("2026-09-20T10:02:00Z")
        val REPLACEMENT_ATTACHED_AT: Instant = Instant.parse("2026-09-20T10:03:00Z")
        val CURRENT_ATTACHED_AT: Instant = Instant.parse("2026-09-20T10:04:00Z")
        val DELETED_AT: Instant = Instant.parse("2026-09-20T10:05:00Z")
        val EXPIRES_AT: Instant = Instant.parse("2026-09-20T11:00:00Z")
        val DOWNLOAD_EXPIRES_AT: Instant = Instant.parse("2026-09-20T10:20:00Z")
        val DOWNLOAD_VALID_FOR: Duration = Duration.ofMinutes(10)
        val CHECKSUM: DocumentSha256 = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32)))
    }
}
