package tech.justdev.application.expense

import jakarta.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.application.shared.TransactionRunner
import tech.justdev.domain.document.entity.DocumentMetadata
import tech.justdev.domain.document.entity.DocumentUploadIntent
import tech.justdev.domain.document.entity.DocumentUploadIntentStatus
import tech.justdev.domain.document.entity.ExpenseSupportingDocumentAttachment
import tech.justdev.domain.document.repository.DocumentUploadIntentRepository
import tech.justdev.domain.document.repository.ExpenseSupportingDocumentAttachmentRepository
import tech.justdev.domain.document.valueobject.DocumentFileName
import tech.justdev.domain.document.valueobject.DocumentMediaType
import tech.justdev.domain.document.valueobject.DocumentSha256
import tech.justdev.domain.document.valueobject.DocumentSize
import tech.justdev.domain.document.valueobject.DocumentStorageKey
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.repository.ExpenseRepository
import tech.justdev.domain.expense.valueobject.ExpenseParticipationDecision
import tech.justdev.domain.expense.valueobject.ExpenseShare
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.infrastructure.persistence.expense.R2dbcExpenseSupportingDocumentReplacement
import tech.justdev.testsupport.PostgresMicronautTest
import tech.justdev.testsupport.expenseId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.testUuid
import java.time.Instant
import java.util.Base64

@PostgresMicronautTest
class ReplaceExpenseSupportingDocumentUseCaseIntegrationTest {
    @Inject
    lateinit var groupRepository: GroupRepository

    @Inject
    lateinit var memberRepository: MemberRepository

    @Inject
    lateinit var expenseRepository: ExpenseRepository

    @Inject
    lateinit var documentUploadIntentRepository: DocumentUploadIntentRepository

    @Inject
    lateinit var attachmentRepository: ExpenseSupportingDocumentAttachmentRepository

    @Inject
    lateinit var replacement: R2dbcExpenseSupportingDocumentReplacement

    @Inject
    lateinit var transactionRunner: TransactionRunner

    @Nested
    inner class Invoke {
        @Test
        fun `should replace an expense document while retaining its history and consuming the new upload intent`() =
            runTest {
                val fixture = persistFixture("replace-success")
                val original = readyIntent("rs-original", fixture)
                val replacement = readyIntent("rs-replacement", fixture)
                persistCurrentAttachment(fixture, original)
                documentUploadIntentRepository.persist(replacement)

                useCase()(
                    ReplaceExpenseSupportingDocumentCommand(
                        group = fixture.group.id,
                        expense = fixture.expense.id,
                        replacedSourceUploadIntent = original.id,
                        replacementUploadIntent = replacement.id,
                        requestedBy = fixture.creator,
                        replacedAt = REPLACED_AT,
                    ),
                )

                assertEquals(
                    listOf(
                        AttachmentSnapshot(original.id, null),
                        AttachmentSnapshot(replacement.id, original.id),
                    ),
                    attachmentRepository
                        .findHistoryByExpenseAndGroup(fixture.expense.id, fixture.group.id)
                        .map { attachment -> AttachmentSnapshot(attachment.sourceUploadIntent, attachment.replacesSourceUploadIntent) },
                )
                assertEquals(
                    listOf(replacement.id),
                    attachmentRepository
                        .findCurrentByExpenseAndGroup(fixture.expense.id, fixture.group.id)
                        .map(ExpenseSupportingDocumentAttachment::sourceUploadIntent),
                )
                assertEquals(
                    DocumentUploadIntentStatus.Consumed(
                        verifiedAt = CREATED_AT.minusSeconds(30),
                        consumedAt = REPLACED_AT,
                    ),
                    requireNotNull(documentUploadIntentRepository.findByIdAndGroup(replacement.id, fixture.group.id)).status,
                )
            }

        @Test
        fun `should keep a new upload intent ready when a concurrent successor makes the replacement branch invalid`() =
            runTest {
                val fixture = persistFixture("replace-branch")
                val original = readyIntent("rb-original", fixture)
                val firstSuccessor = readyIntent("rb-first-successor", fixture)
                val competingSuccessor = readyIntent("rb-competing-successor", fixture)
                persistCurrentAttachment(fixture, original)
                documentUploadIntentRepository.persist(firstSuccessor)
                documentUploadIntentRepository.persist(competingSuccessor)
                useCase()(
                    replacementCommand(
                        fixture = fixture,
                        replacedSourceUploadIntent = original.id,
                        replacementUploadIntent = firstSuccessor.id,
                    ),
                )

                val error =
                    assertThrows<ExpenseSupportingDocumentAttachmentUnavailableException> {
                        useCase()(
                            replacementCommand(
                                fixture = fixture,
                                replacedSourceUploadIntent = original.id,
                                replacementUploadIntent = competingSuccessor.id,
                            ),
                        )
                    }

                assertEquals(
                    "expense supporting document attachment is unavailable",
                    error.message,
                )
                assertEquals(
                    DocumentUploadIntentStatus.Ready(CREATED_AT.minusSeconds(30)),
                    requireNotNull(documentUploadIntentRepository.findByIdAndGroup(competingSuccessor.id, fixture.group.id)).status,
                )
                assertEquals(
                    listOf(
                        AttachmentSnapshot(original.id, null),
                        AttachmentSnapshot(firstSuccessor.id, original.id),
                    ),
                    attachmentRepository
                        .findHistoryByExpenseAndGroup(fixture.expense.id, fixture.group.id)
                        .map { attachment -> AttachmentSnapshot(attachment.sourceUploadIntent, attachment.replacesSourceUploadIntent) },
                )
            }
    }

    @Nested
    inner class InTransaction {
        @Test
        fun `should wait for an accepted expense lock then reject replacement without consuming the upload`() =
            runTest {
                val fixture = persistFixture("replace-accepted-race")
                val original = readyIntent("ra-original", fixture)
                val replacementIntent = readyIntent("ra-replacement", fixture)
                persistCurrentAttachment(fixture, original)
                documentUploadIntentRepository.persist(replacementIntent)
                val acceptedExpense =
                    fixture.expense.recordParticipationDecision(
                        member = memberEmail("replace-accepted-race-bob"),
                        decision = ExpenseParticipationDecision.APPROVE,
                        decidedAt = REPLACED_AT,
                    )
                val releaseAcceptance = CompletableDeferred<Unit>()
                val acceptancePersisted = CompletableDeferred<Unit>()
                val acceptance =
                    async {
                        transactionRunner.transaction {
                            expenseRepository.persist(acceptedExpense)
                            acceptancePersisted.complete(Unit)
                            releaseAcceptance.await()
                        }
                    }
                acceptancePersisted.await()

                val replacementAttempt =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        runCatching {
                            replacement.inTransaction { scope ->
                                val expense = requireNotNull(scope.findExpense(fixture.expense.id, fixture.group.id))
                                expense.requireSupportingDocumentReplacementBy(fixture.creator)
                            }
                        }.exceptionOrNull()
                    }
                yield()

                assertFalse(replacementAttempt.isCompleted)

                releaseAcceptance.complete(Unit)
                acceptance.await()
                val error = replacementAttempt.await()

                assertEquals(IllegalArgumentException::class.java, error?.javaClass)
                assertEquals("supporting documents can only be replaced while the expense is proposed", error?.message)
                assertEquals(
                    DocumentUploadIntentStatus.Ready(CREATED_AT.minusSeconds(30)),
                    requireNotNull(documentUploadIntentRepository.findByIdAndGroup(replacementIntent.id, fixture.group.id)).status,
                )
                assertEquals(
                    listOf(AttachmentSnapshot(original.id, null)),
                    attachmentRepository
                        .findHistoryByExpenseAndGroup(fixture.expense.id, fixture.group.id)
                        .map { attachment -> AttachmentSnapshot(attachment.sourceUploadIntent, attachment.replacesSourceUploadIntent) },
                )
            }
    }

    private fun useCase(): ReplaceExpenseSupportingDocumentUseCase =
        ReplaceExpenseSupportingDocumentUseCaseImpl(
            groupAccessPolicy = GroupAccessPolicy(groupRepository),
            expenseSupportingDocumentReplacement = replacement,
        )

    private fun replacementCommand(
        fixture: Fixture,
        replacedSourceUploadIntent: DocumentUploadIntentId,
        replacementUploadIntent: DocumentUploadIntentId,
    ): ReplaceExpenseSupportingDocumentCommand =
        ReplaceExpenseSupportingDocumentCommand(
            group = fixture.group.id,
            expense = fixture.expense.id,
            replacedSourceUploadIntent = replacedSourceUploadIntent,
            replacementUploadIntent = replacementUploadIntent,
            requestedBy = fixture.creator,
            replacedAt = REPLACED_AT,
        )

    private suspend fun persistFixture(seed: String): Fixture {
        val creator = memberEmail("$seed-alice")
        val participant = memberEmail("$seed-bob")
        memberRepository.persist(Member(creator, CREATED_AT.minusSeconds(120)))
        memberRepository.persist(Member(participant, CREATED_AT.minusSeconds(119)))
        val group =
            Group
                .create(groupId("$seed-group"), creator, CREATED_AT.minusSeconds(100))
                .addMember(participant, CREATED_AT.minusSeconds(99))
        groupRepository.persist(group)
        val expense =
            Expense.propose(
                id = expenseId("$seed-expense"),
                group = group.id,
                title = "Documented repair",
                createdBy = creator,
                totalAmount = MoneyAmount.ofCents(100),
                createdAt = CREATED_AT.minusSeconds(90),
                shares =
                    setOf(
                        ExpenseShare(creator, MoneyAmount.ofCents(50)),
                        ExpenseShare(participant, MoneyAmount.ofCents(50)),
                    ),
            )
        expenseRepository.persist(expense)
        return Fixture(group, expense, creator)
    }

    private suspend fun persistCurrentAttachment(
        fixture: Fixture,
        original: DocumentUploadIntent,
    ) {
        val consumed = original.consume(REPLACED_AT.minusSeconds(1))
        documentUploadIntentRepository.persist(consumed)
        attachmentRepository.persist(
            ExpenseSupportingDocumentAttachment.attach(fixture.expense.id, fixture.group.id, consumed),
        )
    }

    private fun readyIntent(
        seed: String,
        fixture: Fixture,
    ): DocumentUploadIntent =
        DocumentUploadIntent
            .create(
                id = DocumentUploadIntentId(testUuid("$seed:replacement")),
                group = fixture.group.id,
                uploader = fixture.creator,
                storageKey = DocumentStorageKey.of("groups/${fixture.group.id.toPrimitive()}/documents/$seed.pdf"),
                fileName = DocumentFileName.of("$seed.pdf"),
                expectedMetadata = METADATA,
                createdAt = CREATED_AT.minusSeconds(60),
                expiresAt = CREATED_AT.plusSeconds(60),
            ).markReady(METADATA, CREATED_AT.minusSeconds(30))

    private data class Fixture(
        val group: Group,
        val expense: Expense,
        val creator: MemberEmail,
    )

    private data class AttachmentSnapshot(
        val sourceUploadIntent: DocumentUploadIntentId,
        val replacesSourceUploadIntent: DocumentUploadIntentId?,
    )

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-08-11T10:00:00Z")
        val REPLACED_AT: Instant = Instant.parse("2026-08-11T10:00:00Z")
        val METADATA =
            DocumentMetadata(
                mediaType = DocumentMediaType.of("application/pdf"),
                size = DocumentSize.ofBytes(512),
                checksum = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32))),
            )
    }
}
