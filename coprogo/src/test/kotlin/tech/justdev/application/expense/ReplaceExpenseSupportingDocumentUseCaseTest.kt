package tech.justdev.application.expense

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.application.group.GroupNotFoundException
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
import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.exception.ExpenseSupportingDocumentUnavailableException
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

class ReplaceExpenseSupportingDocumentUseCaseTest {
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class Invoke {
        private fun documentChangeGuardCases() =
            listOf(
                DocumentChangeGuardCase(
                    description = "non-creator",
                    expense = proposedExpense(),
                    requestedBy = memberEmail("bob"),
                    expectedMessage = "only the expense creator can change a supporting document",
                ),
                DocumentChangeGuardCase(
                    description = "accepted expense",
                    expense = acceptedExpense(),
                    requestedBy = CREATOR,
                    expectedMessage = "supporting documents can only be changed while the expense is proposed",
                ),
                DocumentChangeGuardCase(
                    description = "invalidated expense",
                    expense = invalidatedExpense(),
                    requestedBy = CREATOR,
                    expectedMessage = "supporting documents can only be changed while the expense is proposed",
                ),
            )

        @Test
        fun `should verify membership before opening the replacement transaction`() {
            val error =
                assertThrows<GroupNotFoundException> {
                    runTest {
                        useCase(
                            groupRepository = RecordingGroupRepository(null),
                            replacementPersistence = FailingExpenseSupportingDocumentReplacementPersistence(),
                        )(command())
                    }
                }

            assertEquals("group ${GROUP.toPrimitive()} was not found", error.message)
        }

        @Test
        fun `should reject an absent expense`() {
            val error =
                assertThrows<ExpenseNotFoundException> {
                    runTest {
                        useCase(replacementPersistence = recordingReplacementPersistence(expense = null))(command())
                    }
                }

            assertEquals(EXPENSE, error.id)
            assertEquals(GROUP, error.group)
        }

        @ParameterizedTest(name = "{index}: {2}")
        @MethodSource("documentChangeGuardCases")
        fun `should enforce creator and proposed status before reading documents`(case: DocumentChangeGuardCase) {
            val error =
                assertThrows<IllegalArgumentException> {
                    runTest {
                        useCase(
                            replacementPersistence =
                                RecordingExpenseSupportingDocumentReplacementPersistence(
                                    FailingDocumentScope(case.expense),
                                ),
                        )(command(requestedBy = case.requestedBy))
                    }
                }

            assertEquals(case.expectedMessage, error.message)
        }

        @Test
        fun `should reject an absent current supporting document`() {
            val error =
                assertThrows<ExpenseSupportingDocumentUnavailableException> {
                    runTest {
                        useCase(replacementPersistence = recordingReplacementPersistence(expense = proposedExpense()))(command())
                    }
                }

            assertEquals("expense supporting document is unavailable", error.message)
        }

        @Test
        fun `should reject an unavailable replacement upload intent`() {
            val error =
                assertThrows<SupportingDocumentUploadIntentUnavailableException> {
                    runTest {
                        useCase(replacementPersistence = recordingReplacementPersistence(intent = null))(command())
                    }
                }

            assertEquals("supporting document upload intent is unavailable", error.message)
        }

        @Test
        fun `should reject consumption outside the replacement upload intent lifetime`() {
            val replacementPersistence = recordingReplacementPersistence(intent = readyIntent("invalid-time"))

            val error =
                assertThrows<SupportingDocumentUploadIntentUnavailableException> {
                    runTest {
                        useCase(replacementPersistence = replacementPersistence)(command(replacedAt = CREATED_AT.minusSeconds(31)))
                    }
                }

            assertEquals("supporting document upload intent is unavailable", error.message)
            assertEquals(emptyList<ReplacementPersistenceSnapshot>(), replacementPersistence.scope.persisted)
        }

        @Test
        fun `should persist the consumed replacement intent and successor document in the transaction`() =
            runTest {
                val replacementIntent = readyIntent("replacement", REPLACEMENT_UPLOAD_INTENT)
                val replacementPersistence = recordingReplacementPersistence(intent = replacementIntent)

                useCase(replacementPersistence = replacementPersistence)(command())

                assertEquals(listOf(ExpenseLookup(EXPENSE, GROUP)), replacementPersistence.scope.expenseLookups)
                assertEquals(
                    listOf(IntentLookup(REPLACEMENT_UPLOAD_INTENT, GROUP, CREATOR)),
                    replacementPersistence.scope.intentLookups,
                )
                assertEquals(
                    listOf(
                        ReplacementPersistenceSnapshot(
                            consumedIntent = replacementIntent.id,
                            consumedStatus = replacementIntent.consume(REPLACED_AT).status,
                            sourceUploadIntent = replacementIntent.id,
                            replacesSourceUploadIntent = ORIGINAL_UPLOAD_INTENT,
                            expense = EXPENSE,
                            group = GROUP,
                        ),
                    ),
                    replacementPersistence.scope.persisted,
                )
            }
    }

    private fun useCase(
        groupRepository: GroupRepository = RecordingGroupRepository(group()),
        replacementPersistence: ExpenseSupportingDocumentReplacementPersistence = recordingReplacementPersistence(),
    ): ReplaceExpenseSupportingDocumentUseCase =
        ReplaceExpenseSupportingDocumentUseCaseImpl(
            groupAccessPolicy = GroupAccessPolicy(groupRepository),
            expenseSupportingDocumentReplacementPersistence = replacementPersistence,
        )

    private fun recordingReplacementPersistence(
        expense: Expense? = proposedExpense().copy(supportingDocuments = ExpenseSupportingDocuments.restore(listOf(currentDocument()))),
        intent: DocumentUploadIntent? = readyIntent("default"),
    ): RecordingExpenseSupportingDocumentReplacementPersistence =
        RecordingExpenseSupportingDocumentReplacementPersistence(RecordingScope(expense, intent))

    private fun command(
        requestedBy: MemberEmail = CREATOR,
        replacedAt: Instant = REPLACED_AT,
    ): ReplaceExpenseSupportingDocumentCommand =
        ReplaceExpenseSupportingDocumentCommand(
            group = GROUP,
            expense = EXPENSE,
            replacedSourceUploadIntent = ORIGINAL_UPLOAD_INTENT,
            replacementUploadIntent = REPLACEMENT_UPLOAD_INTENT,
            requestedBy = requestedBy,
            replacedAt = replacedAt,
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
        proposedExpense().recordParticipationDecision(
            member = memberEmail("bob"),
            decision = ExpenseParticipationDecision.APPROVE,
            decidedAt = REPLACED_AT,
        )

    private fun invalidatedExpense(): Expense =
        proposedExpense().recordParticipationDecision(
            member = memberEmail("bob"),
            decision = ExpenseParticipationDecision.REFUSE,
            decidedAt = REPLACED_AT,
        )

    private fun currentDocument(): ExpenseSupportingDocument =
        ExpenseSupportingDocument.fromConsumedUploadIntent(readyIntent("original", ORIGINAL_UPLOAD_INTENT).consume(REPLACED_AT))

    private fun readyIntent(
        seed: String,
        id: DocumentUploadIntentId = DocumentUploadIntentId(testUuid("$seed:replace-document")),
    ): DocumentUploadIntent =
        DocumentUploadIntent.restore(
            id = id,
            group = GROUP,
            uploader = CREATOR,
            storageKey = DocumentStorageKey.of("groups/${GROUP.toPrimitive()}/documents/$seed.pdf"),
            fileName = DocumentFileName.of("$seed.pdf"),
            expectedMetadata = METADATA,
            createdAt = CREATED_AT.minusSeconds(60),
            expiresAt = CREATED_AT.plusSeconds(60),
            status = DocumentUploadIntentStatus.Ready(CREATED_AT.minusSeconds(30)),
        )

    private class RecordingGroupRepository(
        private val group: Group?,
    ) : GroupRepository {
        override suspend fun findById(id: GroupId): Group? = group?.takeIf { it.id == id }

        override suspend fun persist(group: Group) = Unit
    }

    private class FailingExpenseSupportingDocumentReplacementPersistence : ExpenseSupportingDocumentReplacementPersistence {
        override suspend fun <T> inTransaction(block: suspend (ExpenseSupportingDocumentReplacementPersistenceScope) -> T): Nothing =
            error("replacement transaction must not open before membership validation")
    }

    private class RecordingExpenseSupportingDocumentReplacementPersistence(
        val scope: RecordingScope,
    ) : ExpenseSupportingDocumentReplacementPersistence {
        override suspend fun <T> inTransaction(block: suspend (ExpenseSupportingDocumentReplacementPersistenceScope) -> T): T = block(scope)
    }

    private open class RecordingScope(
        private val expense: Expense?,
        private val intent: DocumentUploadIntent?,
    ) : ExpenseSupportingDocumentReplacementPersistenceScope {
        val persisted = mutableListOf<ReplacementPersistenceSnapshot>()
        val expenseLookups = mutableListOf<ExpenseLookup>()
        val intentLookups = mutableListOf<IntentLookup>()

        override suspend fun findExpense(
            id: tech.justdev.domain.expense.valueobject.ExpenseId,
            group: GroupId,
        ): Expense? {
            expenseLookups += ExpenseLookup(id, group)
            return expense
        }

        override suspend fun findReadyReplacementUploadIntent(
            id: DocumentUploadIntentId,
            group: GroupId,
            uploader: MemberEmail,
        ): DocumentUploadIntent? {
            intentLookups += IntentLookup(id, group, uploader)
            return intent
        }

        override suspend fun persist(
            replacement: tech.justdev.domain.expense.entity.ExpenseSupportingDocumentReplacement,
            consumedReplacementIntent: DocumentUploadIntent,
        ) {
            val replacementDocument = replacement.replacement
            persisted +=
                ReplacementPersistenceSnapshot(
                    consumedIntent = consumedReplacementIntent.id,
                    consumedStatus = consumedReplacementIntent.status,
                    sourceUploadIntent = replacementDocument.sourceUploadIntent,
                    replacesSourceUploadIntent = replacementDocument.replacesSourceUploadIntent,
                    expense = replacement.expense.id,
                    group = replacement.expense.group,
                )
        }
    }

    private class FailingDocumentScope(
        expense: Expense,
    ) : RecordingScope(expense, null) {
        override suspend fun findReadyReplacementUploadIntent(
            id: DocumentUploadIntentId,
            group: GroupId,
            uploader: MemberEmail,
        ): Nothing = error("documents must not be read before the replacement guard")
    }

    private data class ExpenseLookup(
        val expense: tech.justdev.domain.expense.valueobject.ExpenseId,
        val group: GroupId,
    )

    private data class IntentLookup(
        val uploadIntent: DocumentUploadIntentId,
        val group: GroupId,
        val uploader: MemberEmail,
    )

    private data class ReplacementPersistenceSnapshot(
        val consumedIntent: DocumentUploadIntentId,
        val consumedStatus: DocumentUploadIntentStatus,
        val sourceUploadIntent: DocumentUploadIntentId,
        val replacesSourceUploadIntent: DocumentUploadIntentId?,
        val expense: tech.justdev.domain.expense.valueobject.ExpenseId,
        val group: GroupId,
    )

    data class DocumentChangeGuardCase(
        val description: String,
        val expense: Expense,
        val requestedBy: MemberEmail,
        val expectedMessage: String,
    ) {
        override fun toString(): String = description
    }

    private companion object {
        val GROUP = groupId("replace-document-group")
        val EXPENSE = expenseId("replace-document-expense")
        val CREATOR = memberEmail("alice")
        val ORIGINAL_UPLOAD_INTENT = DocumentUploadIntentId(testUuid("original-replace-document"))
        val REPLACEMENT_UPLOAD_INTENT = DocumentUploadIntentId(testUuid("replacement-doc-2026"))
        val CREATED_AT: Instant = Instant.parse("2026-08-10T10:00:00Z")
        val REPLACED_AT: Instant = Instant.parse("2026-08-10T10:00:00Z")
        val METADATA =
            DocumentMetadata(
                mediaType = DocumentMediaType.of("application/pdf"),
                size = DocumentSize.ofBytes(512),
                checksum = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32))),
            )
    }
}
