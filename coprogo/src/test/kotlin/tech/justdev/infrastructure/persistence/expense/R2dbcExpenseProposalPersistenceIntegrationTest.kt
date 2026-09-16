package tech.justdev.infrastructure.persistence.expense

import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.testsupport.PostgresMicronautTest
import tech.justdev.testsupport.expenseId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.testUuid
import java.time.Instant
import java.util.Base64

@PostgresMicronautTest
class R2dbcExpenseProposalPersistenceIntegrationTest {
    @Inject
    lateinit var persistence: R2dbcExpenseProposalPersistence

    @Inject
    lateinit var transactionRunner: TransactionRunner

    @Inject
    lateinit var expenseRepository: ExpenseRepository

    @Inject
    lateinit var documentUploadIntentRepository: DocumentUploadIntentRepository

    @Inject
    lateinit var attachmentRepository: ExpenseSupportingDocumentAttachmentRepository

    @Inject
    lateinit var groupRepository: GroupRepository

    @Inject
    lateinit var memberRepository: MemberRepository

    @Nested
    inner class Persist {
        @Test
        fun `should persist consumed documents then their expense and attachments atomically`() =
            runTest {
                val fixture = persistFixture("proposal-success")
                val expense = expense("proposal-success", fixture)
                val first = readyIntent("first-proposal-success", fixture)
                val second = readyIntent("second-proposal-success", fixture)
                documentUploadIntentRepository.persist(first)
                documentUploadIntentRepository.persist(second)
                val consumed = listOf(first.consume(CREATED_AT), second.consume(CREATED_AT))
                val attachments = consumed.map { ExpenseSupportingDocumentAttachment.attach(expense.id, fixture.group.id, it) }

                persistence.persist(expense, consumed, attachments)

                assertEquals(expense, expenseRepository.findByIdAndGroup(expense.id, fixture.group.id))
                assertEquals(
                    consumed.map(DocumentUploadIntent::id).sortedBy(DocumentUploadIntentId::toPrimitive),
                    attachmentRepository
                        .findCurrentByExpenseAndGroup(expense.id, fixture.group.id)
                        .map(ExpenseSupportingDocumentAttachment::sourceUploadIntent),
                )
                assertEquals(
                    consumed.map(DocumentUploadIntent::status),
                    listOf(first, second)
                        .map { intent ->
                            requireNotNull(documentUploadIntentRepository.findByIdAndGroup(intent.id, fixture.group.id)).status
                        },
                )
            }

        @Test
        fun `should roll back consumed documents and expense when an attachment cannot persist`() =
            runTest {
                val fixture = persistFixture("proposal-rollback")
                val expense = expense("proposal-rollback", fixture)
                val intent = readyIntent("rollback-proposal-document", fixture)
                documentUploadIntentRepository.persist(intent)
                val consumed = intent.consume(CREATED_AT)
                val attachment = ExpenseSupportingDocumentAttachment.attach(expense.id, fixture.group.id, consumed)
                val failingPersistence =
                    R2dbcExpenseProposalPersistence(
                        transactionRunner = transactionRunner,
                        documentUploadIntentRepository = documentUploadIntentRepository,
                        expenseRepository = expenseRepository,
                        attachmentRepository =
                            object : ExpenseSupportingDocumentAttachmentRepository {
                                override suspend fun persist(attachment: ExpenseSupportingDocumentAttachment): Nothing =
                                    error("attachment persistence failed")

                                override suspend fun persistAll(attachments: List<ExpenseSupportingDocumentAttachment>): Nothing =
                                    error("attachment persistence failed")

                                override suspend fun findCurrentByExpenseAndGroup(
                                    expense: ExpenseId,
                                    group: GroupId,
                                ): List<ExpenseSupportingDocumentAttachment> = emptyList()

                                override suspend fun findCurrentBySourceUploadIntentAndExpenseAndGroup(
                                    sourceUploadIntent: DocumentUploadIntentId,
                                    expense: ExpenseId,
                                    group: GroupId,
                                ): ExpenseSupportingDocumentAttachment? = null

                                override suspend fun findHistoryByExpenseAndGroup(
                                    expense: ExpenseId,
                                    group: GroupId,
                                ): List<ExpenseSupportingDocumentAttachment> = emptyList()
                            },
                    )

                val error =
                    assertThrows<IllegalStateException> {
                        failingPersistence.persist(expense, listOf(consumed), listOf(attachment))
                    }

                assertEquals("attachment persistence failed", error.message)
                assertNull(expenseRepository.findByIdAndGroup(expense.id, fixture.group.id))
                assertEquals(
                    DocumentUploadIntentStatus.Ready(CREATED_AT.minusSeconds(30)),
                    requireNotNull(documentUploadIntentRepository.findByIdAndGroup(intent.id, fixture.group.id)).status,
                )
            }
    }

    private suspend fun persistFixture(seed: String): Fixture {
        val creator = memberEmail("$seed-alice")
        memberRepository.persist(Member(creator, CREATED_AT.minusSeconds(120)))
        val group = Group.create(groupId("$seed-group"), creator, CREATED_AT.minusSeconds(90))
        groupRepository.persist(group)
        return Fixture(group, creator)
    }

    private fun expense(
        seed: String,
        fixture: Fixture,
    ): Expense =
        Expense.proposeEqualSplit(
            id = expenseId("$seed-expense"),
            group = fixture.group.id,
            title = "Documented repair",
            createdBy = fixture.creator,
            totalAmount = MoneyAmount.ofCents(100),
            createdAt = CREATED_AT,
            participants = setOf(fixture.creator),
        )

    private fun readyIntent(
        seed: String,
        fixture: Fixture,
    ): DocumentUploadIntent =
        DocumentUploadIntent
            .create(
                id = DocumentUploadIntentId(testUuid("$seed:expense-proposal")),
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
        val creator: MemberEmail,
    )

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-08-01T10:00:00Z")
        val METADATA =
            DocumentMetadata(
                mediaType = DocumentMediaType.of("application/pdf"),
                size = DocumentSize.ofBytes(512),
                checksum = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32))),
            )
    }
}
