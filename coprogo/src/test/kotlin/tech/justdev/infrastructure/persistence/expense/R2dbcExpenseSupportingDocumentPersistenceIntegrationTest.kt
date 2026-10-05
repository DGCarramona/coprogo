package tech.justdev.infrastructure.persistence.expense

import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tech.justdev.domain.document.entity.DocumentMetadata
import tech.justdev.domain.document.entity.DocumentUploadIntent
import tech.justdev.domain.document.entity.DocumentUploadIntentStatus
import tech.justdev.domain.document.repository.DocumentUploadIntentRepository
import tech.justdev.domain.document.valueobject.DocumentFileName
import tech.justdev.domain.document.valueobject.DocumentMediaType
import tech.justdev.domain.document.valueobject.DocumentSha256
import tech.justdev.domain.document.valueobject.DocumentSize
import tech.justdev.domain.document.valueobject.DocumentStorageKey
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.repository.ExpenseRepository
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.testsupport.PostgresMicronautTest
import tech.justdev.testsupport.expenseId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.testUuid
import java.time.Instant
import java.util.Base64
import java.util.UUID

@PostgresMicronautTest
class R2dbcExpenseSupportingDocumentPersistenceIntegrationTest {
    @Inject
    lateinit var replacementPersistence: R2dbcExpenseSupportingDocumentReplacementPersistence

    @Inject
    lateinit var deletionPersistence: R2dbcExpenseSupportingDocumentDeletionPersistence

    @Inject
    lateinit var expenseRepository: ExpenseRepository

    @Inject
    lateinit var documentUploadIntentRepository: DocumentUploadIntentRepository

    @Inject
    lateinit var groupRepository: GroupRepository

    @Inject
    lateinit var memberRepository: MemberRepository

    @Nested
    inner class ReplacementTransaction {
        @Test
        fun `should lock load replace and persist an expense document with its consumed intent`() =
            runTest {
                val fixture = persistedDocumentedExpense("replacement")
                val replacementIntent = readyIntent("replacement", fixture.seed, fixture.group, fixture.creator)
                documentUploadIntentRepository.persist(replacementIntent)

                replacementPersistence.inTransaction { scope ->
                    val expense = requireNotNull(scope.findExpense(fixture.expense.id, fixture.group.id))
                    val readyIntent =
                        requireNotNull(
                            scope.findReadyReplacementUploadIntent(
                                replacementIntent.id,
                                fixture.group.id,
                                fixture.creator,
                            ),
                        )
                    val consumedIntent = readyIntent.consume(CHANGED_AT)
                    scope.persist(
                        expense.replaceSupportingDocument(
                            sourceUploadIntent = fixture.originalIntent.id,
                            replacementIntent = consumedIntent,
                            requestedBy = fixture.creator,
                        ),
                        consumedIntent,
                    )
                }

                val stored = requireNotNull(expenseRepository.findByIdAndGroup(fixture.expense.id, fixture.group.id))
                assertEquals(
                    listOf(fixture.originalIntent.id, replacementIntent.id),
                    stored.supportingDocuments.all.map { document -> document.sourceUploadIntent },
                )
                assertEquals(
                    DocumentUploadIntentStatus.Consumed(CREATED_AT.minusSeconds(30), CHANGED_AT),
                    requireNotNull(documentUploadIntentRepository.findByIdAndGroup(replacementIntent.id, fixture.group.id)).status,
                )
            }

        @Test
        fun `should return null for a replacement intent owned by another uploader`() =
            runTest {
                val fixture = persistedDocumentedExpense("replacement-uploader")
                val replacementIntent = readyIntent("replacement-uploader", fixture.seed, fixture.group, fixture.creator)
                documentUploadIntentRepository.persist(replacementIntent)

                val found =
                    replacementPersistence.inTransaction { scope ->
                        scope.findReadyReplacementUploadIntent(
                            replacementIntent.id,
                            fixture.group.id,
                            memberEmail("${fixture.seed}-other"),
                        )
                    }

                assertEquals(null, found)
            }
    }

    @Nested
    inner class DeletionTransaction {
        @Test
        fun `should lock load and persist an audited expense document deletion`() =
            runTest {
                val fixture = persistedDocumentedExpense("deletion")

                deletionPersistence.inTransaction { scope ->
                    val expense = requireNotNull(scope.findExpense(fixture.expense.id, fixture.group.id))
                    scope.persist(
                        expense.deleteSupportingDocument(
                            sourceUploadIntent = fixture.originalIntent.id,
                            requestedBy = fixture.creator,
                            deletedAt = CHANGED_AT,
                        ),
                    )
                }

                val stored = requireNotNull(expenseRepository.findByIdAndGroup(fixture.expense.id, fixture.group.id))
                assertEquals(emptyList<DocumentUploadIntentId>(), stored.supportingDocuments.current.map { it.sourceUploadIntent })
                assertEquals(
                    CHANGED_AT,
                    stored.supportingDocuments.all
                        .single()
                        .deletion
                        ?.deletedAt,
                )
            }
    }

    private suspend fun persistedDocumentedExpense(label: String): Fixture {
        val seed = "${UUID.randomUUID()}-$label"
        val creator = memberEmail("$seed-creator")
        val participant = memberEmail("$seed-participant")
        memberRepository.persist(Member(creator, CREATED_AT.minusSeconds(120)))
        memberRepository.persist(Member(participant, CREATED_AT.minusSeconds(119)))
        val group =
            Group
                .create(groupId("$seed-group"), creator, CREATED_AT.minusSeconds(90))
                .addMember(participant, CREATED_AT.minusSeconds(89))
        groupRepository.persist(group)
        val expense =
            Expense.proposeEqualSplit(
                id = expenseId("$seed-expense"),
                group = group.id,
                title = "Documented repair",
                createdBy = creator,
                totalAmount = MoneyAmount.ofCents(100),
                createdAt = CREATED_AT,
                participants = setOf(creator, participant),
            )
        val original = readyIntent("original", seed, group, creator)
        val consumedOriginal = original.consume(CREATED_AT.plusSeconds(1))
        documentUploadIntentRepository.persist(consumedOriginal)
        val documentedExpense = expense.attachSupportingDocuments(listOf(consumedOriginal))
        expenseRepository.persist(documentedExpense)
        return Fixture(seed, group, creator, documentedExpense, original)
    }

    private fun readyIntent(
        label: String,
        seed: String,
        group: Group,
        creator: MemberEmail,
    ): DocumentUploadIntent =
        DocumentUploadIntent
            .create(
                id = DocumentUploadIntentId(testUuid("$label:$seed")),
                group = group.id,
                uploader = creator,
                storageKey = DocumentStorageKey.of("groups/${group.id.toPrimitive()}/documents/$label.pdf"),
                fileName = DocumentFileName.of("$label.pdf"),
                expectedMetadata = METADATA,
                createdAt = CREATED_AT.minusSeconds(60),
                expiresAt = CREATED_AT.plusSeconds(60),
            ).markReady(METADATA, CREATED_AT.minusSeconds(30))

    private data class Fixture(
        val seed: String,
        val group: Group,
        val creator: MemberEmail,
        val expense: Expense,
        val originalIntent: DocumentUploadIntent,
    )

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-08-12T10:00:00Z")
        val CHANGED_AT: Instant = Instant.parse("2026-08-12T10:00:30Z")
        val METADATA =
            DocumentMetadata(
                mediaType = DocumentMediaType.of("application/pdf"),
                size = DocumentSize.ofBytes(512),
                checksum = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32))),
            )
    }
}
