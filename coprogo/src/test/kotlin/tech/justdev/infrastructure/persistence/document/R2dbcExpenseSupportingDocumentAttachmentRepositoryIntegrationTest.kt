package tech.justdev.infrastructure.persistence.document

import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.domain.document.entity.DocumentMetadata
import tech.justdev.domain.document.entity.DocumentUploadIntent
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
class R2dbcExpenseSupportingDocumentAttachmentRepositoryIntegrationTest {
    @Inject
    lateinit var repository: ExpenseSupportingDocumentAttachmentRepository

    @Inject
    lateinit var uploadIntentRepository: DocumentUploadIntentRepository

    @Inject
    lateinit var expenseRepository: ExpenseRepository

    @Inject
    lateinit var memberRepository: MemberRepository

    @Inject
    lateinit var groupRepository: GroupRepository

    @Nested
    inner class Persist {
        @Test
        fun `should persist multiple supporting documents for the same expense`() =
            runTest {
                val group = seedGroup("multiple")
                val expense = seedExpense("multiple", group)
                val first = attachment("multiple-first", group, expense)
                val second = attachment("multiple-second", group, expense)

                repository.persist(first)
                repository.persist(second)

                assertAttachmentsEqual(
                    listOf(first, second).sortedBy { it.sourceUploadIntent.toPrimitive() },
                    repository.findByExpenseAndGroup(expense, group.id),
                )
            }

        @Test
        fun `should accept a repeated persist of the exact immutable association`() =
            runTest {
                val group = seedGroup("repeat")
                val expense = seedExpense("repeat", group)
                val attachment = attachment("repeat", group, expense)

                repository.persist(attachment)
                repository.persist(attachment)

                assertAttachmentsEqual(listOf(attachment), repository.findByExpenseAndGroup(expense, group.id))
            }

        @Test
        fun `should reject reusing an upload intent for another expense without changing the original association`() =
            runTest {
                val group = seedGroup("conflict")
                val firstExpense = seedExpense("conflict-first", group)
                val secondExpense = seedExpense("conflict-second", group)
                val stored = attachment("conflict", group, firstExpense)
                repository.persist(stored)

                val error =
                    assertThrows<IllegalStateException> {
                        repository.persist(
                            ExpenseSupportingDocumentAttachment.restore(
                                sourceUploadIntent = stored.sourceUploadIntent,
                                group = group.id,
                                expense = secondExpense,
                            ),
                        )
                    }

                assertEquals("supporting document attachment is already associated with another expense", error.message)
                assertAttachmentsEqual(listOf(stored), repository.findByExpenseAndGroup(firstExpense, group.id))
                assertTrue(repository.findByExpenseAndGroup(secondExpense, group.id).isEmpty())
            }

        @Test
        fun `should reject reusing an upload intent in another group`() =
            runTest {
                val sourceGroup = seedGroup("source-group-conflict")
                val sourceExpense = seedExpense("source-group-conflict", sourceGroup)
                val otherGroup = seedGroup("other-group-conflict")
                val otherExpense = seedExpense("other-group-conflict", otherGroup)
                val stored = attachment("group-conflict", sourceGroup, sourceExpense)
                repository.persist(stored)

                val error =
                    assertThrows<IllegalStateException> {
                        repository.persist(
                            ExpenseSupportingDocumentAttachment.restore(
                                sourceUploadIntent = stored.sourceUploadIntent,
                                group = otherGroup.id,
                                expense = otherExpense,
                            ),
                        )
                    }

                assertEquals("supporting document attachment is already associated with another resource", error.message)
                assertAttachmentsEqual(listOf(stored), repository.findByExpenseAndGroup(sourceExpense, sourceGroup.id))
                assertTrue(repository.findByExpenseAndGroup(otherExpense, otherGroup.id).isEmpty())
            }

        @Test
        fun `should roll back a newly inserted parent when the expense association is invalid`() =
            runTest {
                val sourceGroup = seedGroup("rollback-source")
                val sourceExpense = seedExpense("rollback-source", sourceGroup)
                val otherGroup = seedGroup("rollback-other")
                val otherExpense = seedExpense("rollback-other", otherGroup)
                val valid = attachment("rollback", sourceGroup, sourceExpense)

                val error =
                    assertThrows<IllegalStateException> {
                        repository.persist(
                            ExpenseSupportingDocumentAttachment.restore(
                                sourceUploadIntent = valid.sourceUploadIntent,
                                group = sourceGroup.id,
                                expense = otherExpense,
                            ),
                        )
                    }

                assertEquals("expense supporting document attachment must reference an expense in the same group", error.message)

                repository.persist(valid)

                assertAttachmentsEqual(listOf(valid), repository.findByExpenseAndGroup(sourceExpense, sourceGroup.id))
            }
    }

    @Nested
    inner class FindByExpenseAndGroup {
        @Test
        fun `should return an empty list when the expense is missing`() =
            runTest {
                val group = seedGroup("missing")

                assertTrue(repository.findByExpenseAndGroup(expenseId("missing"), group.id).isEmpty())
            }

        @Test
        fun `should not expose attachments from another group`() =
            runTest {
                val sourceGroup = seedGroup("scoped-source")
                val expense = seedExpense("scoped", sourceGroup)
                val attachment = attachment("scoped", sourceGroup, expense)
                val otherGroup = seedGroup("scoped-other")
                repository.persist(attachment)

                assertTrue(repository.findByExpenseAndGroup(expense, otherGroup.id).isEmpty())
            }
    }

    private suspend fun attachment(
        seed: String,
        group: SeededGroup,
        expense: ExpenseId,
    ): ExpenseSupportingDocumentAttachment {
        val intent = consumedIntent(seed, group)
        uploadIntentRepository.persist(intent)
        return ExpenseSupportingDocumentAttachment.attach(expense = expense, group = group.id, intent = intent)
    }

    private suspend fun seedGroup(seed: String): SeededGroup {
        val uploader = memberEmail("$seed-owner")
        val group = groupId(seed)
        memberRepository.persist(Member(uploader, CREATED_AT))
        groupRepository.persist(Group.create(group, uploader, CREATED_AT))
        return SeededGroup(id = group, owner = uploader)
    }

    private suspend fun seedExpense(
        seed: String,
        group: SeededGroup,
    ): ExpenseId {
        val participant = memberEmail("$seed-participant")
        memberRepository.persist(Member(participant, CREATED_AT))
        val expense =
            Expense.proposeEqualSplit(
                id = expenseId(seed),
                group = group.id,
                title = "Expense $seed",
                createdBy = group.owner,
                totalAmount = MoneyAmount.ofCents(100),
                createdAt = CREATED_AT,
                participants = setOf(group.owner, participant),
            )
        expenseRepository.persist(expense)
        return expense.id
    }

    private fun consumedIntent(
        seed: String,
        group: SeededGroup,
    ): DocumentUploadIntent =
        DocumentUploadIntent
            .create(
                id = DocumentUploadIntentId(testUuid("ai:$seed")),
                group = group.id,
                uploader = group.owner,
                storageKey = DocumentStorageKey.of("groups/${group.id.toPrimitive()}/documents/$seed.pdf"),
                fileName = DocumentFileName.of("Facture $seed.pdf"),
                expectedMetadata = METADATA,
                createdAt = CREATED_AT,
                expiresAt = EXPIRES_AT,
            ).markReady(METADATA, READY_AT)
            .consume(CONSUMED_AT)

    private fun assertAttachmentsEqual(
        expected: List<ExpenseSupportingDocumentAttachment>,
        actual: List<ExpenseSupportingDocumentAttachment>,
    ) {
        assertEquals(expected.map { it.toProperties() }, actual.map { it.toProperties() })
    }

    private fun ExpenseSupportingDocumentAttachment.toProperties(): AttachmentProperties =
        AttachmentProperties(sourceUploadIntent, group, expense)

    private data class SeededGroup(
        val id: GroupId,
        val owner: MemberEmail,
    )

    private data class AttachmentProperties(
        val sourceUploadIntent: DocumentUploadIntentId,
        val group: GroupId,
        val expense: ExpenseId,
    )

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-08-30T10:00:00Z")
        val READY_AT: Instant = Instant.parse("2026-08-30T10:01:00Z")
        val CONSUMED_AT: Instant = Instant.parse("2026-08-30T10:02:00Z")
        val EXPIRES_AT: Instant = Instant.parse("2026-08-30T10:05:00Z")
        val METADATA =
            DocumentMetadata(
                mediaType = DocumentMediaType.of("application/pdf"),
                size = DocumentSize.ofBytes(512),
                checksum = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32))),
            )
    }
}
