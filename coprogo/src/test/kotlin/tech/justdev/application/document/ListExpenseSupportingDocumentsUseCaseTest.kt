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
import tech.justdev.domain.expense.valueobject.ExpenseParticipationDecision
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
                        )(query())
                    }
                }

            assertEquals(EXPENSE, error.id)
            assertEquals(GROUP, error.group)
            assertEquals(listOf("membership", "expense"), interactions)
            assertEquals(listOf(ExpenseLookup(EXPENSE, GROUP)), expenseRepository.lookups)
        }

        @Test
        fun `should return empty current and history`() =
            runTest {
                val interactions = mutableListOf<String>()

                val result =
                    useCase(
                        groupRepository = RecordingGroupRepository(interactions, group()),
                        expenseRepository = RecordingExpenseRepository(interactions, expense()),
                    )(query())

                assertEquals(ListExpenseSupportingDocumentsResult(emptyList(), emptyList()), result)
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

                val result =
                    useCase(
                        groupRepository = RecordingGroupRepository(mutableListOf(), group()),
                        expenseRepository = RecordingExpenseRepository(mutableListOf(), expense(history)),
                    )(query())

                assertEquals(
                    ListExpenseSupportingDocumentsResult(
                        current = emptyList(),
                        history =
                            listOf(
                                listedDocument(
                                    document = original,
                                    canDelete = false,
                                ),
                                listedDocument(
                                    document = replacement,
                                    canDelete = false,
                                ),
                            ),
                    ),
                    result,
                )
            }

        @Test
        fun `should expose deletion capability only for current documents requested by the creator on a proposed expense`() =
            runTest {
                val original = document(consumedIntent(ORIGINAL, "original.pdf", "application/pdf", 512, ORIGINAL_ATTACHED_AT))
                val current =
                    document(
                        consumedIntent(CURRENT, "current.pdf", "application/pdf", 1_024, CURRENT_ATTACHED_AT),
                        replaces = ORIGINAL,
                    )
                val expense = expense(listOf(original, current))

                val creatorResult =
                    useCase(
                        groupRepository = RecordingGroupRepository(mutableListOf(), group()),
                        expenseRepository = RecordingExpenseRepository(mutableListOf(), expense),
                    )(query())
                val otherMemberResult =
                    useCase(
                        groupRepository = RecordingGroupRepository(mutableListOf(), group()),
                        expenseRepository = RecordingExpenseRepository(mutableListOf(), expense),
                    )(query(requestedBy = OUTSIDER_MEMBER))

                assertEquals(listOf(true), creatorResult.current.map(ListedExpenseSupportingDocument::canDelete))
                assertEquals(listOf(false, true), creatorResult.history.map(ListedExpenseSupportingDocument::canDelete))
                assertEquals(listOf(false), otherMemberResult.current.map(ListedExpenseSupportingDocument::canDelete))
                assertEquals(listOf(false, false), otherMemberResult.history.map(ListedExpenseSupportingDocument::canDelete))
            }

        @Test
        fun `should not expose deletion capability after the expense is accepted or invalidated`() =
            runTest {
                val document = document(consumedIntent(CURRENT, "current.pdf", "application/pdf", 512, CURRENT_ATTACHED_AT))
                val proposed = expense(listOf(document))
                val accepted =
                    proposed.recordParticipationDecision(
                        member = OUTSIDER_MEMBER,
                        decision = ExpenseParticipationDecision.APPROVE,
                        decidedAt = CURRENT_ATTACHED_AT.plusSeconds(1),
                    )
                val invalidated =
                    proposed.recordParticipationDecision(
                        member = OUTSIDER_MEMBER,
                        decision = ExpenseParticipationDecision.REFUSE,
                        decidedAt = CURRENT_ATTACHED_AT.plusSeconds(1),
                    )

                val results =
                    listOf(accepted, invalidated).map { terminalExpense ->
                        useCase(
                            groupRepository = RecordingGroupRepository(mutableListOf(), group()),
                            expenseRepository = RecordingExpenseRepository(mutableListOf(), terminalExpense),
                        )(query())
                    }

                assertEquals(
                    listOf(listOf(false), listOf(false)),
                    results.map { result -> result.current.map(ListedExpenseSupportingDocument::canDelete) },
                )
            }
    }

    private fun useCase(
        groupRepository: GroupRepository,
        expenseRepository: ExpenseRepository,
    ): ListExpenseSupportingDocumentsUseCase =
        ListExpenseSupportingDocumentsUseCaseImpl(
            groupAccessPolicy = GroupAccessPolicy(groupRepository),
            expenseRepository = expenseRepository,
        )

    private fun query(requestedBy: MemberEmail = UPLOADER): ListExpenseSupportingDocumentsQuery =
        ListExpenseSupportingDocumentsQuery(
            group = GROUP,
            expense = EXPENSE,
            requestedBy = requestedBy,
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

    private fun listedDocument(
        document: ExpenseSupportingDocument,
        canDelete: Boolean,
    ): ListedExpenseSupportingDocument =
        ListedExpenseSupportingDocument(
            document = document,
            canDelete = canDelete,
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
        val CHECKSUM: DocumentSha256 = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32)))
    }
}
