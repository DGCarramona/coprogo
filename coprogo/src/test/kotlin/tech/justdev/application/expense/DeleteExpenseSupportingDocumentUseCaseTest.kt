package tech.justdev.application.expense

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.application.group.GroupNotFoundException
import tech.justdev.domain.document.entity.DocumentMetadata
import tech.justdev.domain.document.entity.DocumentUploadIntent
import tech.justdev.domain.document.entity.ExpenseSupportingDocumentAttachment
import tech.justdev.domain.document.valueobject.DocumentFileName
import tech.justdev.domain.document.valueobject.DocumentMediaType
import tech.justdev.domain.document.valueobject.DocumentSha256
import tech.justdev.domain.document.valueobject.DocumentSize
import tech.justdev.domain.document.valueobject.DocumentStorageKey
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.exception.ExpenseSupportingDocumentAttachmentUnavailableException
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

class DeleteExpenseSupportingDocumentUseCaseTest {
    @Nested
    inner class Invoke {
        @Test
        fun `should verify membership before opening the deletion transaction`() {
            val error =
                assertThrows<GroupNotFoundException> {
                    runTest {
                        useCase(
                            groupRepository = RecordingGroupRepository(null),
                            deletionPersistence = FailingExpenseSupportingDocumentDeletionPersistence(),
                        )(command())
                    }
                }

            assertEquals("group ${GROUP.toPrimitive()} was not found", error.message)
        }

        @Test
        fun `should reject an absent expense`() {
            val error =
                assertThrows<ExpenseNotFoundException> {
                    runTest { useCase(deletionPersistence = recordingDeletionPersistence(expense = null))(command()) }
                }

            assertEquals(EXPENSE, error.id)
            assertEquals(GROUP, error.group)
        }

        @Test
        fun `should enforce creator and proposed status before reading the attachment`() {
            val outcomes =
                listOf(
                    proposedExpense() to memberEmail("bob"),
                    acceptedExpense() to CREATOR,
                    invalidatedExpense() to CREATOR,
                ).map { (expense, requestedBy) ->
                    runCatching {
                        runTest {
                            useCase(
                                deletionPersistence =
                                    RecordingExpenseSupportingDocumentDeletionPersistence(
                                        FailingAttachmentScope(expense),
                                    ),
                            )(command(requestedBy = requestedBy))
                        }
                    }.exceptionOrNull()?.let { error -> DeletionGuardOutcome(error.javaClass, error.message) }
                }

            assertEquals(
                listOf(
                    DeletionGuardOutcome(
                        IllegalArgumentException::class.java,
                        "only the expense creator can change a supporting document",
                    ),
                    DeletionGuardOutcome(
                        IllegalArgumentException::class.java,
                        "supporting documents can only be changed while the expense is proposed",
                    ),
                    DeletionGuardOutcome(
                        IllegalArgumentException::class.java,
                        "supporting documents can only be changed while the expense is proposed",
                    ),
                ),
                outcomes,
            )
        }

        @Test
        fun `should reject an absent current attachment`() {
            val error =
                assertThrows<ExpenseSupportingDocumentAttachmentUnavailableException> {
                    runTest { useCase(deletionPersistence = recordingDeletionPersistence(expense = proposedExpense()))(command()) }
                }

            assertEquals("expense supporting document attachment is unavailable", error.message)
        }

        @Test
        fun `should persist the exact audited deletion in the transaction`() =
            runTest {
                val deletionPersistence = recordingDeletionPersistence()

                useCase(deletionPersistence = deletionPersistence)(command())

                assertEquals(listOf(ExpenseLookup(EXPENSE, GROUP)), deletionPersistence.scope.expenseLookups)
                assertEquals(
                    listOf(
                        DeletionPersistenceSnapshot(
                            sourceUploadIntent = UPLOAD_INTENT,
                            expense = EXPENSE,
                            group = GROUP,
                            deletedBy = CREATOR,
                            deletedAt = DELETED_AT,
                        ),
                    ),
                    deletionPersistence.scope.persisted,
                )
            }
    }

    private fun useCase(
        groupRepository: GroupRepository = RecordingGroupRepository(group()),
        deletionPersistence: ExpenseSupportingDocumentDeletionPersistence = recordingDeletionPersistence(),
    ): DeleteExpenseSupportingDocumentUseCase =
        DeleteExpenseSupportingDocumentUseCaseImpl(
            groupAccessPolicy = GroupAccessPolicy(groupRepository),
            expenseSupportingDocumentDeletionPersistence = deletionPersistence,
        )

    private fun recordingDeletionPersistence(
        expense: Expense? = proposedExpense().copy(supportingDocuments = listOf(currentAttachment())),
    ): RecordingExpenseSupportingDocumentDeletionPersistence =
        RecordingExpenseSupportingDocumentDeletionPersistence(
            RecordingScope(expense),
        )

    private fun command(requestedBy: MemberEmail = CREATOR): DeleteExpenseSupportingDocumentCommand =
        DeleteExpenseSupportingDocumentCommand(
            group = GROUP,
            expense = EXPENSE,
            sourceUploadIntent = UPLOAD_INTENT,
            requestedBy = requestedBy,
            deletedAt = DELETED_AT,
        )

    private fun group(): Group =
        Group
            .create(GROUP, CREATOR, CREATED_AT.minusSeconds(120))
            .addMember(memberEmail("bob"), CREATED_AT.minusSeconds(119))

    private fun proposedExpense(): Expense =
        Expense.propose(
            id = EXPENSE,
            group = GROUP,
            title = "Documented repair",
            createdBy = CREATOR,
            totalAmount = MoneyAmount.ofCents(100),
            createdAt = CREATED_AT,
            shares =
                setOf(
                    ExpenseShare(CREATOR, MoneyAmount.ofCents(50)),
                    ExpenseShare(memberEmail("bob"), MoneyAmount.ofCents(50)),
                ),
        )

    private fun acceptedExpense(): Expense =
        proposedExpense().recordParticipationDecision(memberEmail("bob"), ExpenseParticipationDecision.APPROVE, DELETED_AT)

    private fun invalidatedExpense(): Expense =
        proposedExpense().recordParticipationDecision(memberEmail("bob"), ExpenseParticipationDecision.REFUSE, DELETED_AT)

    private fun currentAttachment(): ExpenseSupportingDocumentAttachment =
        ExpenseSupportingDocumentAttachment.attach(
            expense = EXPENSE,
            group = GROUP,
            intent =
                DocumentUploadIntent
                    .create(
                        id = UPLOAD_INTENT,
                        group = GROUP,
                        uploader = CREATOR,
                        storageKey = DocumentStorageKey.of("groups/${GROUP.toPrimitive()}/documents/original.pdf"),
                        fileName = DocumentFileName.of("original.pdf"),
                        expectedMetadata = METADATA,
                        createdAt = CREATED_AT.minusSeconds(60),
                        expiresAt = CREATED_AT.plusSeconds(60),
                    ).markReady(METADATA, CREATED_AT.minusSeconds(30))
                    .consume(CREATED_AT.minusSeconds(1)),
        )

    private class RecordingGroupRepository(
        private val group: Group?,
    ) : GroupRepository {
        override suspend fun findById(id: GroupId): Group? = group?.takeIf { it.id == id }

        override suspend fun persist(group: Group) = Unit
    }

    private class FailingExpenseSupportingDocumentDeletionPersistence : ExpenseSupportingDocumentDeletionPersistence {
        override suspend fun <T> inTransaction(block: suspend (ExpenseSupportingDocumentDeletionPersistenceScope) -> T): Nothing =
            error("deletion transaction must not open before membership validation")
    }

    private class RecordingExpenseSupportingDocumentDeletionPersistence(
        val scope: RecordingScope,
    ) : ExpenseSupportingDocumentDeletionPersistence {
        override suspend fun <T> inTransaction(block: suspend (ExpenseSupportingDocumentDeletionPersistenceScope) -> T): T = block(scope)
    }

    private open class RecordingScope(
        private val expense: Expense?,
    ) : ExpenseSupportingDocumentDeletionPersistenceScope {
        val persisted = mutableListOf<DeletionPersistenceSnapshot>()
        val expenseLookups = mutableListOf<ExpenseLookup>()

        override suspend fun findExpense(
            id: tech.justdev.domain.expense.valueobject.ExpenseId,
            group: GroupId,
        ): Expense? {
            expenseLookups += ExpenseLookup(id, group)
            return expense
        }

        override suspend fun persist(deletion: tech.justdev.domain.expense.entity.ExpenseSupportingDocumentDeletion) {
            val deletedAttachment = deletion.deleted
            val deletion = requireNotNull(deletedAttachment.deletion)
            persisted +=
                DeletionPersistenceSnapshot(
                    sourceUploadIntent = deletedAttachment.sourceUploadIntent,
                    expense = deletedAttachment.expense,
                    group = deletedAttachment.group,
                    deletedBy = deletion.deletedBy,
                    deletedAt = deletion.deletedAt,
                )
        }
    }

    private class FailingAttachmentScope(
        expense: Expense,
    ) : RecordingScope(expense) {
        override suspend fun persist(deletion: tech.justdev.domain.expense.entity.ExpenseSupportingDocumentDeletion): Nothing =
            error("attachment must not be changed before the expense document guard")
    }

    private data class ExpenseLookup(
        val id: tech.justdev.domain.expense.valueobject.ExpenseId,
        val group: GroupId,
    )

    private data class DeletionGuardOutcome(
        val type: Class<*>?,
        val message: String?,
    )

    private data class DeletionPersistenceSnapshot(
        val sourceUploadIntent: DocumentUploadIntentId,
        val expense: tech.justdev.domain.expense.valueobject.ExpenseId,
        val group: GroupId,
        val deletedBy: MemberEmail,
        val deletedAt: Instant,
    )

    private companion object {
        val GROUP: GroupId = groupId("delete-document-group")
        val EXPENSE = expenseId("delete-document-expense")
        val CREATOR: MemberEmail = memberEmail("alice")
        val UPLOAD_INTENT = DocumentUploadIntentId(testUuid("delete-document-upload-intent"))
        val CREATED_AT: Instant = Instant.parse("2026-08-11T10:00:00Z")
        val DELETED_AT: Instant = Instant.parse("2026-08-11T10:01:00Z")
        val METADATA =
            DocumentMetadata(
                mediaType = DocumentMediaType.of("application/pdf"),
                size = DocumentSize.ofBytes(512),
                checksum = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32))),
            )
    }
}
