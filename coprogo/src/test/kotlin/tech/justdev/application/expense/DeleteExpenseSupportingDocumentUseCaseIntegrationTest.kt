package tech.justdev.application.expense

import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.domain.document.entity.DocumentMetadata
import tech.justdev.domain.document.entity.DocumentUploadIntent
import tech.justdev.domain.document.repository.DocumentUploadIntentRepository
import tech.justdev.domain.document.valueobject.DocumentFileName
import tech.justdev.domain.document.valueobject.DocumentMediaType
import tech.justdev.domain.document.valueobject.DocumentSha256
import tech.justdev.domain.document.valueobject.DocumentSize
import tech.justdev.domain.document.valueobject.DocumentStorageKey
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.repository.ExpenseRepository
import tech.justdev.domain.expense.valueobject.ExpenseShare
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.infrastructure.persistence.expense.R2dbcExpenseSupportingDocumentDeletionPersistence
import tech.justdev.testsupport.PostgresMicronautTest
import tech.justdev.testsupport.expenseId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.testUuid
import java.time.Instant
import java.util.Base64

@PostgresMicronautTest
class DeleteExpenseSupportingDocumentUseCaseIntegrationTest {
    @Inject
    lateinit var groupRepository: GroupRepository

    @Inject
    lateinit var memberRepository: MemberRepository

    @Inject
    lateinit var expenseRepository: ExpenseRepository

    @Inject
    lateinit var documentUploadIntentRepository: DocumentUploadIntentRepository

    @Inject
    lateinit var deletionPersistence: R2dbcExpenseSupportingDocumentDeletionPersistence

    @Nested
    inner class Invoke {
        @Test
        fun `should tombstone a current expense document while retaining its audited history`() =
            runTest {
                val fixture = persistFixture("delete-success")
                val uploadIntent = readyIntent("delete-success-original", fixture)
                persistCurrentAttachment(fixture, uploadIntent)

                useCase()(
                    DeleteExpenseSupportingDocumentCommand(
                        group = fixture.group.id,
                        expense = fixture.expense.id,
                        sourceUploadIntent = uploadIntent.id,
                        requestedBy = fixture.creator,
                        deletedAt = DELETED_AT,
                    ),
                )

                val expense = requireNotNull(expenseRepository.findByIdAndGroup(fixture.expense.id, fixture.group.id))
                assertEquals(
                    emptyList<DocumentUploadIntentId>(),
                    expense.supportingDocuments.current.map { document -> document.sourceUploadIntent },
                )
                assertEquals(
                    listOf(HistorySnapshot(uploadIntent.id, fixture.creator, DELETED_AT)),
                    expense.supportingDocuments.all.map { document ->
                        HistorySnapshot(
                            sourceUploadIntent = document.sourceUploadIntent,
                            deletedBy = document.deletion?.deletedBy,
                            deletedAt = document.deletion?.deletedAt,
                        )
                    },
                )
            }
    }

    private fun useCase(): DeleteExpenseSupportingDocumentUseCase =
        DeleteExpenseSupportingDocumentUseCaseImpl(
            groupAccessPolicy = GroupAccessPolicy(groupRepository),
            expenseSupportingDocumentDeletionPersistence = deletionPersistence,
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
        uploadIntent: DocumentUploadIntent,
    ) {
        val consumed = uploadIntent.consume(CREATED_AT.minusSeconds(1))
        documentUploadIntentRepository.persist(consumed)
        expenseRepository.persist(fixture.expense.attachSupportingDocuments(listOf(consumed)))
    }

    private fun readyIntent(
        seed: String,
        fixture: Fixture,
    ): DocumentUploadIntent =
        DocumentUploadIntent
            .create(
                id = DocumentUploadIntentId(testUuid("$seed:upload-intent")),
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

    private data class HistorySnapshot(
        val sourceUploadIntent: DocumentUploadIntentId,
        val deletedBy: MemberEmail?,
        val deletedAt: Instant?,
    )

    private companion object {
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
