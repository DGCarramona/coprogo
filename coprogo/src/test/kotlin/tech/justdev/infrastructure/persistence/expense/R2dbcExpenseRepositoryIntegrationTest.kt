package tech.justdev.infrastructure.persistence.expense

import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
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
import tech.justdev.domain.expense.valueobject.RefusalReason
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.repository.MemberRepository
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
class R2dbcExpenseRepositoryIntegrationTest {
    @Inject
    lateinit var expenseRepository: ExpenseRepository

    @Inject
    lateinit var memberRepository: MemberRepository

    @Inject
    lateinit var groupRepository: GroupRepository

    @Inject
    lateinit var documentUploadIntentRepository: DocumentUploadIntentRepository

    @Inject
    lateinit var attachmentRepository: ExpenseSupportingDocumentAttachmentRepository

    @Nested
    inner class Persist {
        @Test
        fun `should persist and retrieve an expense with participations`() =
            runTest {
                val stored = expenseWithEqualSplit("persist-expense")

                expenseRepository.persist(stored)

                assertEquals(stored, expenseRepository.findByIdAndGroup(stored.id, stored.group))
            }

        @Test
        fun `should replace stored participations on second persist`() =
            runTest {
                val owner = memberEmail("replace-persist-owner")
                val firstMember = memberEmail("replace-persist-first")
                val group = groupId("replace-persist-group")
                persistMember(owner)
                persistMember(firstMember)
                persistGroup(group, owner)

                val initial =
                    Expense.proposeEqualSplit(
                        id = expenseId("replace-persist"),
                        group = group,
                        title = "replace",
                        createdBy = owner,
                        totalAmount = MoneyAmount.ofCents(1000),
                        createdAt = Instant.parse("2026-06-01T10:00:00Z"),
                        participants = setOf(owner, firstMember),
                    )

                expenseRepository.persist(initial)

                val secondMember = memberEmail("replace-persist-second")
                persistMember(secondMember)

                val updated =
                    Expense.propose(
                        id = expenseId("replace-persist"),
                        group = group,
                        title = "replace",
                        createdBy = owner,
                        totalAmount = MoneyAmount.ofCents(1500),
                        createdAt = Instant.parse("2026-06-01T10:00:00Z"),
                        shares =
                            setOf(
                                ExpenseShare(owner, MoneyAmount.ofCents(1000)),
                                ExpenseShare(secondMember, MoneyAmount.ofCents(500)),
                            ),
                    )

                expenseRepository.persist(updated)

                assertEquals(updated, expenseRepository.findByIdAndGroup(updated.id, updated.group))
            }

        @Test
        fun `should round trip refusal reasons including a missing reason`() =
            runTest {
                val withReason =
                    expenseWithEqualSplit("reason-present").recordParticipationDecision(
                        member = memberEmail("reason-present-participant"),
                        decision = ExpenseParticipationDecision.REFUSE,
                        decidedAt = Instant.parse("2026-06-01T12:00:00Z"),
                        reason = RefusalReason.of("The amount does not match the invoice"),
                    )
                val withoutReason =
                    expenseWithEqualSplit("reason-missing").recordParticipationDecision(
                        member = memberEmail("reason-missing-participant"),
                        decision = ExpenseParticipationDecision.REFUSE,
                        decidedAt = Instant.parse("2026-06-01T12:00:00Z"),
                    )

                expenseRepository.persist(withReason)
                expenseRepository.persist(withoutReason)

                assertEquals(withReason, expenseRepository.findByIdAndGroup(withReason.id, withReason.group))
                assertEquals(withoutReason, expenseRepository.findByIdAndGroup(withoutReason.id, withoutReason.group))
            }
    }

    @Nested
    inner class FindByIdAndGroup {
        @Test
        fun `should find a persisted expense for its group`() =
            runTest {
                val stored = expenseWithEqualSplit("find-expense")
                expenseRepository.persist(stored)

                assertEquals(stored, expenseRepository.findByIdAndGroup(stored.id, stored.group))
            }

        @Test
        fun `should hydrate every current supporting document without historical versions`() =
            runTest {
                val stored = expenseWithEqualSplit("doc-find-expense")
                expenseRepository.persist(stored)
                val original = persistCurrentAttachment(stored, "original")
                val replacement = persistReplacementAttachment(original, "replacement")
                val deleted = persistCurrentAttachment(stored, "deleted")
                attachmentRepository.persist(deleted.delete(stored.createdBy, Instant.parse("2026-06-01T11:00:00Z")))

                val found = requireNotNull(expenseRepository.findByIdAndGroup(stored.id, stored.group))

                assertEquals(stored, found.copy(supportingDocuments = emptyList()))
                assertEquals(listOf(replacement.snapshot()), found.supportingDocuments.map { attachment -> attachment.snapshot() })
            }

        @Test
        fun `should return null when no expense exists for the id`() =
            runTest {
                assertNull(
                    expenseRepository.findByIdAndGroup(
                        expenseId("missing-expense"),
                        groupId("missing-expense-group"),
                    ),
                )
            }

        @Test
        fun `should return null when the expense id belongs to another group`() =
            runTest {
                val stored = expenseWithEqualSplit("find-expense-other-group")
                expenseRepository.persist(stored)

                assertNull(
                    expenseRepository.findByIdAndGroup(
                        stored.id,
                        groupId("find-expense-requested-group"),
                    ),
                )
            }
    }

    @Nested
    inner class FindProposedByIdAndGroup {
        @Test
        fun `should return null when the expense is accepted`() =
            runTest {
                val stored = expenseWithEqualSplit("proposed-expense-accepted")
                expenseRepository.persist(stored)

                val participant = memberEmail("proposed-expense-accepted-participant")
                val updated =
                    stored.recordParticipationDecision(
                        member = participant,
                        decision = ExpenseParticipationDecision.APPROVE,
                        decidedAt = Instant.parse("2026-06-01T12:00:00Z"),
                    )

                expenseRepository.persist(updated)

                assertNull(expenseRepository.findProposedByIdAndGroup(updated.id, updated.group))
            }

        @Test
        fun `should return null when the expense is invalidated`() =
            runTest {
                val stored = expenseWithEqualSplit("proposed-expense-refused")
                expenseRepository.persist(stored)

                val participant = memberEmail("proposed-expense-refused-participant")
                val updated =
                    stored.recordParticipationDecision(
                        member = participant,
                        decision = ExpenseParticipationDecision.REFUSE,
                        decidedAt = Instant.parse("2026-06-01T12:00:00Z"),
                    )

                expenseRepository.persist(updated)

                assertNull(expenseRepository.findProposedByIdAndGroup(updated.id, updated.group))
            }

        @Test
        fun `should return the expense when still proposed`() =
            runTest {
                val stored = expenseWithEqualSplit("proposed-expense-pending")
                expenseRepository.persist(stored)

                assertEquals(stored, expenseRepository.findProposedByIdAndGroup(stored.id, stored.group))
            }

        @Test
        fun `should hydrate current supporting documents when still proposed`() =
            runTest {
                val stored = expenseWithEqualSplit("doc-proposed-expense")
                expenseRepository.persist(stored)
                val attachment = persistCurrentAttachment(stored, "proposed")

                val found = requireNotNull(expenseRepository.findProposedByIdAndGroup(stored.id, stored.group))

                assertEquals(stored, found.copy(supportingDocuments = emptyList()))
                assertEquals(
                    listOf(attachment.snapshot()),
                    found.supportingDocuments.map { currentAttachment ->
                        currentAttachment.snapshot()
                    },
                )
            }

        @Test
        fun `should return null when the expense id belongs to another group`() =
            runTest {
                val stored = expenseWithEqualSplit("proposed-expense-other-group")
                expenseRepository.persist(stored)

                assertNull(
                    expenseRepository.findProposedByIdAndGroup(
                        stored.id,
                        groupId("requested-other-group"),
                    ),
                )
            }
    }

    @Nested
    inner class FindByGroup {
        @Test
        fun `should return expenses for a specific group`() =
            runTest {
                val groupA = groupId("ga")
                val groupB = groupId("gb")
                val owner = memberEmail("findbygroup-owner")
                persistMember(owner)
                persistGroup(groupA, owner)
                persistGroup(groupB, owner)

                val expense1 =
                    expenseWithEqualSplit(
                        seed = "fbge1",
                        groupOverride = groupA,
                        ownerOverride = owner,
                    )
                val expense2 =
                    expenseWithEqualSplit(
                        seed = "fbge2",
                        groupOverride = groupA,
                        ownerOverride = owner,
                    )
                expenseRepository.persist(expense1)
                expenseRepository.persist(expense2)

                val expense3 =
                    expenseWithEqualSplit(
                        seed = "fbge3",
                        groupOverride = groupB,
                        ownerOverride = owner,
                    )
                expenseRepository.persist(expense3)

                val groupAExpenses = expenseRepository.findByGroup(groupA)
                assertEquals(setOf(expense1, expense2), groupAExpenses.toSet())

                val groupBExpenses = expenseRepository.findByGroup(groupB)
                assertEquals(setOf(expense3), groupBExpenses.toSet())
            }

        @Test
        fun `should return empty list when group has no expenses`() =
            runTest {
                val emptyGroup = groupId("fbge")
                val owner = memberEmail("fbge-owner")
                persistMember(owner)
                persistGroup(emptyGroup, owner)

                val result = expenseRepository.findByGroup(emptyGroup)
                assertTrue(result.isEmpty())
            }

        @Test
        fun `should hydrate documents for the full group through the aggregate reads`() =
            runTest {
                val group = groupId("doc-group")
                val owner = memberEmail("doc-group-owner")
                persistMember(owner)
                persistGroup(group, owner)
                val first = expenseWithEqualSplit("doc-group-first", groupOverride = group, ownerOverride = owner)
                val second = expenseWithEqualSplit("doc-group-second", groupOverride = group, ownerOverride = owner)
                expenseRepository.persist(first)
                expenseRepository.persist(second)
                val firstAttachment = persistCurrentAttachment(first, "group-first")
                val secondAttachment = persistCurrentAttachment(second, "group-second")

                assertEquals(
                    mapOf(first.id to listOf(firstAttachment.snapshot()), second.id to listOf(secondAttachment.snapshot())),
                    expenseRepository
                        .findByGroup(first.group)
                        .filter { expense -> expense.id in setOf(first.id, second.id) }
                        .associate { expense -> expense.id to expense.supportingDocuments.map { attachment -> attachment.snapshot() } },
                )
            }
    }

    private suspend fun expenseWithEqualSplit(
        seed: String,
        groupOverride: GroupId? = null,
        ownerOverride: tech.justdev.domain.group.valueobject.MemberEmail? = null,
    ): Expense {
        val owner = ownerOverride ?: memberEmail("$seed-owner")
        val participant = memberEmail("$seed-participant")
        val group = groupOverride ?: groupId(seed)
        persistMember(owner)
        persistMember(participant)
        persistGroup(group, owner)

        return Expense.proposeEqualSplit(
            id = expenseId(seed),
            group = group,
            title = seed,
            createdBy = owner,
            totalAmount = MoneyAmount.ofCents(1000),
            createdAt = Instant.parse("2026-06-01T10:00:00Z"),
            participants = setOf(owner, participant),
        )
    }

    private suspend fun persistGroup(
        id: GroupId,
        owner: tech.justdev.domain.group.valueobject.MemberEmail,
    ) {
        groupRepository.persist(Group.create(id = id, createdBy = owner, createdAt = Instant.parse("2026-04-13T10:00:00Z")))
    }

    private suspend fun persistMember(email: tech.justdev.domain.group.valueobject.MemberEmail) {
        memberRepository.persist(
            Member(
                email = email,
                createdAt = Instant.parse("2026-04-13T10:00:00Z"),
            ),
        )
    }

    private suspend fun persistCurrentAttachment(
        expense: Expense,
        seed: String,
    ): ExpenseSupportingDocumentAttachment {
        val intent = readyIntent(expense, seed)
        documentUploadIntentRepository.persist(intent)
        val consumed = intent.consume(Instant.parse("2026-06-01T10:30:00Z"))
        documentUploadIntentRepository.persist(consumed)
        val attachment = ExpenseSupportingDocumentAttachment.attach(expense.id, expense.group, consumed)
        attachmentRepository.persist(attachment)
        return attachment
    }

    private suspend fun persistReplacementAttachment(
        replaced: ExpenseSupportingDocumentAttachment,
        seed: String,
    ): ExpenseSupportingDocumentAttachment {
        val expense = requireNotNull(expenseRepository.findByIdAndGroup(replaced.expense, replaced.group))
        val intent = readyIntent(expense, seed)
        documentUploadIntentRepository.persist(intent)
        val consumed = intent.consume(Instant.parse("2026-06-01T10:30:00Z"))
        documentUploadIntentRepository.persist(consumed)
        val replacement = replaced.replaceWith(consumed)
        attachmentRepository.persist(replacement)
        return replacement
    }

    private fun readyIntent(
        expense: Expense,
        seed: String,
    ): DocumentUploadIntent =
        DocumentUploadIntent.restore(
            id = DocumentUploadIntentId(testUuid("erd-$seed")),
            group = expense.group,
            uploader = expense.createdBy,
            storageKey = DocumentStorageKey.of("groups/${expense.group.toPrimitive()}/documents/$seed.pdf"),
            fileName = DocumentFileName.of("$seed.pdf"),
            expectedMetadata = DOCUMENT_METADATA,
            createdAt = Instant.parse("2026-06-01T09:00:00Z"),
            expiresAt = Instant.parse("2026-06-01T11:00:00Z"),
            status = DocumentUploadIntentStatus.Ready(Instant.parse("2026-06-01T10:00:00Z")),
        )

    private fun ExpenseSupportingDocumentAttachment.snapshot() =
        SupportingDocumentSnapshot(
            sourceUploadIntent = sourceUploadIntent,
            replacesSourceUploadIntent = replacesSourceUploadIntent,
        )

    private data class SupportingDocumentSnapshot(
        val sourceUploadIntent: DocumentUploadIntentId,
        val replacesSourceUploadIntent: DocumentUploadIntentId?,
    )

    private companion object {
        val DOCUMENT_METADATA =
            DocumentMetadata(
                mediaType = DocumentMediaType.of("application/pdf"),
                size = DocumentSize.ofBytes(128),
                checksum = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32))),
            )
    }
}
