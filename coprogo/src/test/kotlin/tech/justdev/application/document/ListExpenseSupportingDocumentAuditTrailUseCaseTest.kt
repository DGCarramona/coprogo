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
import java.time.Instant
import java.util.Base64
import java.util.UUID

class ListExpenseSupportingDocumentAuditTrailUseCaseTest {
    @Nested
    inner class Invoke {
        @Test
        fun `should return the complete audit timeline ordered by occurrence action and document`() =
            runTest {
                val original = document(ORIGINAL, "original.pdf", ORIGINAL_ATTACHED_AT)
                val attachedSecond = document(ATTACHED_SECOND, "second.pdf", SAME_OCCURRED_AT)
                val deleted =
                    document(
                        DELETED,
                        "deleted.pdf",
                        SAME_OCCURRED_AT,
                        deletion = SupportingDocumentAttachmentDeletion(UPLOADER, SAME_OCCURRED_AT),
                    )
                val replacement = document(REPLACEMENT, "replacement.pdf", SAME_OCCURRED_AT, replaces = ORIGINAL)

                val result = useCase(expense = expense(listOf(original, replacement, deleted, attachedSecond)))(query())

                assertEquals(
                    listOf(
                        snapshot(
                            ExpenseSupportingDocumentAuditAction.ATTACHED,
                            ORIGINAL_ATTACHED_AT,
                            UPLOADER,
                            ORIGINAL,
                            null,
                            "original.pdf",
                        ),
                        snapshot(
                            ExpenseSupportingDocumentAuditAction.ATTACHED,
                            SAME_OCCURRED_AT,
                            UPLOADER,
                            ATTACHED_SECOND,
                            null,
                            "second.pdf",
                        ),
                        snapshot(
                            ExpenseSupportingDocumentAuditAction.ATTACHED,
                            SAME_OCCURRED_AT,
                            UPLOADER,
                            DELETED,
                            null,
                            "deleted.pdf",
                        ),
                        snapshot(
                            ExpenseSupportingDocumentAuditAction.REPLACED,
                            SAME_OCCURRED_AT,
                            UPLOADER,
                            REPLACEMENT,
                            ORIGINAL,
                            "replacement.pdf",
                        ),
                        snapshot(
                            ExpenseSupportingDocumentAuditAction.DELETED,
                            SAME_OCCURRED_AT,
                            UPLOADER,
                            DELETED,
                            null,
                            "deleted.pdf",
                        ),
                    ),
                    result,
                )
            }

        @Test
        fun `should return an empty timeline for an expense without supporting documents`() =
            runTest {
                val result = useCase(expense = expense())(query())

                assertEquals(emptyList<ExpenseSupportingDocumentAuditEntrySnapshot>(), result)
            }

        @Test
        fun `should reject an absent expense`() {
            val error =
                assertThrows<ExpenseNotFoundException> {
                    runTest { useCase(expense = null)(query()) }
                }

            assertEquals(EXPENSE, error.id)
            assertEquals(GROUP, error.group)
        }

        @Test
        fun `should verify membership before reading the expense`() {
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
    }

    private fun useCase(
        groupRepository: GroupRepository = RecordingGroupRepository(mutableListOf(), group()),
        expenseRepository: ExpenseRepository? = null,
        expense: Expense? = expense(),
    ): ListExpenseSupportingDocumentAuditTrailUseCase =
        ListExpenseSupportingDocumentAuditTrailUseCaseImpl(
            groupAccessPolicy = GroupAccessPolicy(groupRepository),
            expenseRepository = expenseRepository ?: RecordingExpenseRepository(expense),
        )

    private fun query(requestedBy: MemberEmail = UPLOADER): ListExpenseSupportingDocumentAuditTrailQuery =
        ListExpenseSupportingDocumentAuditTrailQuery(GROUP, EXPENSE, requestedBy)

    private fun group(): Group = Group.create(GROUP, UPLOADER, CREATED_AT).addMember(MEMBER, CREATED_AT.plusSeconds(1))

    private fun expense(documents: List<ExpenseSupportingDocument> = emptyList()): Expense =
        Expense
            .propose(
                id = EXPENSE,
                group = GROUP,
                title = "Documented repair",
                createdBy = UPLOADER,
                totalAmount = MoneyAmount.ofCents(100),
                createdAt = CREATED_AT,
                shares = setOf(ExpenseShare(UPLOADER, MoneyAmount.ofCents(50)), ExpenseShare(MEMBER, MoneyAmount.ofCents(50))),
            ).copy(supportingDocuments = ExpenseSupportingDocuments.restore(documents))

    private fun document(
        id: DocumentUploadIntentId,
        fileName: String,
        attachedAt: Instant,
        replaces: DocumentUploadIntentId? = null,
        deletion: SupportingDocumentAttachmentDeletion? = null,
    ): ExpenseSupportingDocument =
        ExpenseSupportingDocument.restore(
            sourceUploadIntent = id,
            uploader = UPLOADER,
            storageKey = DocumentStorageKey.of("documents/${id.toPrimitive()}"),
            fileName = DocumentFileName.of(fileName),
            metadata = DocumentMetadata(DocumentMediaType.of("application/pdf"), DocumentSize.ofBytes(512), CHECKSUM),
            attachedAt = attachedAt,
            replacesSourceUploadIntent = replaces,
            deletion = deletion,
        )

    private fun snapshot(
        action: ExpenseSupportingDocumentAuditAction,
        occurredAt: Instant,
        performedBy: MemberEmail,
        documentUploadIntent: DocumentUploadIntentId,
        replacedDocumentUploadIntent: DocumentUploadIntentId?,
        fileName: String,
    ): ExpenseSupportingDocumentAuditEntrySnapshot =
        ExpenseSupportingDocumentAuditEntrySnapshot(
            action = action,
            occurredAt = occurredAt,
            performedBy = performedBy,
            documentUploadIntent = documentUploadIntent,
            replacedDocumentUploadIntent = replacedDocumentUploadIntent,
            fileName = DocumentFileName.of(fileName),
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
        private val expense: Expense?,
    ) : ExpenseRepository {
        override suspend fun findByIdAndGroup(
            id: ExpenseId,
            group: GroupId,
        ): Expense? = expense?.takeIf { it.id == id && it.group == group }

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

    private companion object {
        val GROUP: GroupId = groupId("document-audit-group")
        val EXPENSE: ExpenseId = expenseId("document-audit-expense")
        val UPLOADER: MemberEmail = memberEmail("alice")
        val MEMBER: MemberEmail = memberEmail("bob")
        val OUTSIDER: MemberEmail = memberEmail("outsider")
        val ORIGINAL: DocumentUploadIntentId = DocumentUploadIntentId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val ATTACHED_SECOND: DocumentUploadIntentId = DocumentUploadIntentId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
        val DELETED: DocumentUploadIntentId = DocumentUploadIntentId(UUID.fromString("00000000-0000-0000-0000-000000000003"))
        val REPLACEMENT: DocumentUploadIntentId = DocumentUploadIntentId(UUID.fromString("00000000-0000-0000-0000-000000000004"))
        val CREATED_AT: Instant = Instant.parse("2026-09-20T10:00:00Z")
        val ORIGINAL_ATTACHED_AT: Instant = Instant.parse("2026-09-20T10:01:00Z")
        val SAME_OCCURRED_AT: Instant = Instant.parse("2026-09-20T10:02:00Z")
        val CHECKSUM: DocumentSha256 = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32)))
    }
}
