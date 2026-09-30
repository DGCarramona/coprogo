package tech.justdev.application.expense

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import tech.justdev.application.group.GroupAccessDeniedException
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.application.support.InMemoryExpenseRepository
import tech.justdev.application.support.InMemoryGroupRepository
import tech.justdev.domain.document.entity.DocumentMetadata
import tech.justdev.domain.document.entity.DocumentUploadIntent
import tech.justdev.domain.document.entity.ExpenseSupportingDocument
import tech.justdev.domain.document.entity.ExpenseSupportingDocuments
import tech.justdev.domain.document.repository.DocumentUploadIntentRepository
import tech.justdev.domain.document.valueobject.DocumentFileName
import tech.justdev.domain.document.valueobject.DocumentMediaType
import tech.justdev.domain.document.valueobject.DocumentSha256
import tech.justdev.domain.document.valueobject.DocumentSize
import tech.justdev.domain.document.valueobject.DocumentStorageKey
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.repository.ExpenseRepository
import tech.justdev.domain.expense.valueobject.ExpenseParticipation
import tech.justdev.domain.expense.valueobject.ExpenseParticipationStatus
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.testsupport.FixedExpenseIdGenerator
import tech.justdev.testsupport.expenseId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.testUuid
import java.time.Instant
import java.util.Base64

class ProposeExpenseUseCaseTest {
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class Invoke {
        private fun unavailableIntentCases(): List<UnavailableIntentCase> {
            val crossGroup = readyIntent("cross-group", group = groupId("another-group"))
            val wrongUploader = readyIntent("wrong-uploader", uploader = memberEmail("bob"))
            val pending = pendingIntent("pending")
            val consumed = readyIntent("consumed").consume(CREATED_AT)
            return listOf(
                UnavailableIntentCase(emptyList(), readyIntent("missing").id),
                UnavailableIntentCase(listOf(crossGroup), crossGroup.id),
                UnavailableIntentCase(listOf(wrongUploader), wrongUploader.id),
                UnavailableIntentCase(listOf(pending), pending.id),
                UnavailableIntentCase(listOf(consumed), consumed.id),
            )
        }

        @Test
        fun `should submit an expense and its consumed upload intents to atomic proposal persistence`() {
            runTest {
                val first = readyIntent("first")
                val second = readyIntent("second")
                val persistence = RecordingExpenseProposalPersistence()
                val documentUploadIntentRepository = RecordingDocumentUploadIntentRepository(listOf(second, first))
                useCase(
                    FixedExpenseIdGenerator(listOf(expenseId("documented-expense"))),
                    documentUploadIntentRepository,
                    persistence,
                )(
                    ProposeExpenseCommand(
                        group = groupId("group-1"),
                        title = "Documented repair",
                        createdBy = memberEmail("alice"),
                        totalAmountInCents = 100,
                        createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                        allocation = EqualSplitExpenseAllocationCommand(setOf(memberEmail("alice"))),
                        supportingDocumentUploadIntents = setOf(second.id, first.id),
                    ),
                )

                val persisted = persistence.persisted.single()
                assertEquals(
                    Expense.proposeEqualSplit(
                        id = expenseId("documented-expense"),
                        group = groupId("group-1"),
                        title = "Documented repair",
                        createdBy = memberEmail("alice"),
                        totalAmount = MoneyAmount.ofCents(100),
                        createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                        participants = setOf(memberEmail("alice")),
                    ),
                    persisted.expense.copy(supportingDocuments = ExpenseSupportingDocuments.empty()),
                )
                assertEquals(
                    listOf(first.id, second.id),
                    persisted.consumedUploadIntents.map(DocumentUploadIntent::id),
                )
                assertEquals(
                    listOf(first.consume(CREATED_AT).status, second.consume(CREATED_AT).status),
                    persisted.consumedUploadIntents.map(DocumentUploadIntent::status),
                )
                assertEquals(
                    listOf(first.id, second.id),
                    persisted.expense.supportingDocuments.current
                        .map(ExpenseSupportingDocument::sourceUploadIntent),
                )
            }
        }

        @ParameterizedTest(name = "{index}")
        @MethodSource("unavailableIntentCases")
        fun `should reject unavailable supporting document upload intents before generating an expense id`(case: UnavailableIntentCase) {
            runTest {
                val persistence = RecordingExpenseProposalPersistence()

                val error =
                    assertThrows<SupportingDocumentUploadIntentUnavailableException> {
                        useCase(
                            ExpenseIdGenerator { throw AssertionError("expense id should not be generated") },
                            RecordingDocumentUploadIntentRepository(case.stored),
                            persistence,
                        )(documentedCommand(case.requested))
                    }

                assertEquals("supporting document upload intent is unavailable", error.message)
                assertEquals(emptyList<PersistedExpenseProposal>(), persistence.persisted)
            }
        }

        @Test
        fun `should verify group membership before reading supporting document upload intents`() {
            val documentUploadIntentRepository =
                object : DocumentUploadIntentRepository {
                    override suspend fun persist(intent: DocumentUploadIntent) = Unit

                    override suspend fun persistAll(intents: List<DocumentUploadIntent>) = Unit

                    override suspend fun findPendingByIdAndGroupAndUploader(
                        id: DocumentUploadIntentId,
                        group: GroupId,
                        uploader: MemberEmail,
                    ): DocumentUploadIntent? = error("not used")

                    override suspend fun findByIdAndGroup(
                        id: DocumentUploadIntentId,
                        group: GroupId,
                    ): DocumentUploadIntent? = error("document upload intent must not be read before membership validation")

                    override suspend fun findReadyByIdsAndGroupAndUploader(
                        ids: Set<DocumentUploadIntentId>,
                        group: GroupId,
                        uploader: MemberEmail,
                    ): List<DocumentUploadIntent> = error("document upload intent must not be read before membership validation")
                }

            assertThrows<GroupAccessDeniedException> {
                runTest {
                    useCase(
                        ExpenseIdGenerator { throw AssertionError("expense id should not be generated") },
                        documentUploadIntentRepository,
                        RecordingExpenseProposalPersistence(),
                    )(
                        documentedCommand(readyIntent("membership").id, createdBy = memberEmail("outsider")),
                    )
                }
            }
        }

        @Test
        fun `invoke should create and persist a proposed expense with equal split participations`() {
            runTest {
                val expenseRepository = InMemoryExpenseRepository()
                val useCase =
                    useCase(
                        expenseRepository,
                        FixedExpenseIdGenerator(listOf(expenseId("expense-1"))),
                    )

                useCase(
                    ProposeExpenseCommand(
                        group = groupId("group-1"),
                        title = "Boiler repair",
                        createdBy = memberEmail("alice"),
                        totalAmountInCents = 100,
                        createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                        allocation =
                            EqualSplitExpenseAllocationCommand(
                                participants =
                                    setOf(
                                        memberEmail("alice"),
                                        memberEmail("bob"),
                                        memberEmail("carol"),
                                    ),
                            ),
                    ),
                )

                assertEquals(
                    Expense(
                        id = expenseId("expense-1"),
                        group = groupId("group-1"),
                        title = "Boiler repair",
                        createdBy = memberEmail("alice"),
                        totalAmount = MoneyAmount.ofCents(100),
                        createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                        participations =
                            setOf(
                                savedExpenseParticipation(
                                    "alice",
                                    34,
                                    ExpenseParticipationStatus.Approved(
                                        Instant.parse("2026-04-03T10:00:00Z"),
                                    ),
                                ),
                                savedExpenseParticipation("bob", 33, ExpenseParticipationStatus.Pending),
                                savedExpenseParticipation("carol", 33, ExpenseParticipationStatus.Pending),
                            ),
                    ),
                    expenseRepository.findByIdAndGroup(expenseId("expense-1"), groupId("group-1")),
                )
            }
        }

        @Test
        fun `invoke should create and persist a proposed expense with equal split caps`() {
            runTest {
                val expenseRepository = InMemoryExpenseRepository()
                val useCase =
                    useCase(
                        expenseRepository,
                        FixedExpenseIdGenerator(listOf(expenseId("expense-3"))),
                    )

                useCase(
                    ProposeExpenseCommand(
                        group = groupId("group-1"),
                        title = "Shared repair with cap",
                        createdBy = memberEmail("alice"),
                        totalAmountInCents = 100,
                        createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                        allocation =
                            EqualSplitWithCapsExpenseAllocationCommand(
                                participants =
                                    setOf(
                                        memberEmail("alice"),
                                        memberEmail("bob"),
                                        memberEmail("carol"),
                                    ),
                                capsInCentsByMember = mapOf(memberEmail("bob") to 20),
                            ),
                    ),
                )

                assertEquals(
                    Expense(
                        id = expenseId("expense-3"),
                        group = groupId("group-1"),
                        title = "Shared repair with cap",
                        createdBy = memberEmail("alice"),
                        totalAmount = MoneyAmount.ofCents(100),
                        createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                        participations =
                            setOf(
                                savedExpenseParticipation(
                                    "alice",
                                    40,
                                    ExpenseParticipationStatus.Approved(
                                        Instant.parse("2026-04-03T10:00:00Z"),
                                    ),
                                ),
                                savedExpenseParticipation("bob", 20, ExpenseParticipationStatus.Pending),
                                savedExpenseParticipation("carol", 40, ExpenseParticipationStatus.Pending),
                            ),
                    ),
                    expenseRepository.findByIdAndGroup(expenseId("expense-3"), groupId("group-1")),
                )
            }
        }

        @Test
        fun `invoke should create and persist a proposed expense with cumulative tiers`() {
            runTest {
                val expenseRepository = InMemoryExpenseRepository()
                val useCase =
                    useCase(
                        expenseRepository,
                        FixedExpenseIdGenerator(listOf(expenseId("expense-4"))),
                    )

                useCase(
                    ProposeExpenseCommand(
                        group = groupId("group-1"),
                        title = "Tiered shared repair",
                        createdBy = memberEmail("alice"),
                        totalAmountInCents = 100,
                        createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                        allocation =
                            CumulativeTiersExpenseAllocationCommand(
                                tiers =
                                    listOf(
                                        CumulativeExpenseTierCommand(
                                            upToAmountInCents = 40,
                                            participants = setOf(memberEmail("alice"), memberEmail("bob")),
                                        ),
                                        CumulativeExpenseTierCommand(
                                            upToAmountInCents = 100,
                                            participants =
                                                setOf(
                                                    memberEmail("alice"),
                                                    memberEmail("bob"),
                                                    memberEmail("carol"),
                                                ),
                                        ),
                                    ),
                            ),
                    ),
                )

                assertEquals(
                    Expense(
                        id = expenseId("expense-4"),
                        group = groupId("group-1"),
                        title = "Tiered shared repair",
                        createdBy = memberEmail("alice"),
                        totalAmount = MoneyAmount.ofCents(100),
                        createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                        participations =
                            setOf(
                                savedExpenseParticipation(
                                    "alice",
                                    40,
                                    ExpenseParticipationStatus.Approved(
                                        Instant.parse("2026-04-03T10:00:00Z"),
                                    ),
                                ),
                                savedExpenseParticipation("bob", 40, ExpenseParticipationStatus.Pending),
                                savedExpenseParticipation("carol", 20, ExpenseParticipationStatus.Pending),
                            ),
                    ),
                    expenseRepository.findByIdAndGroup(expenseId("expense-4"), groupId("group-1")),
                )
            }
        }

        @Test
        fun `invoke should accept immediately when creator is the only participant`() {
            runTest {
                val expenseRepository = InMemoryExpenseRepository()
                val useCase =
                    useCase(
                        expenseRepository,
                        FixedExpenseIdGenerator(listOf(expenseId("expense-2"))),
                    )

                useCase(
                    ProposeExpenseCommand(
                        group = groupId("group-1"),
                        title = "Private purchase",
                        createdBy = memberEmail("alice"),
                        totalAmountInCents = 100,
                        createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                        allocation =
                            CustomExpenseAllocationCommand(
                                participations =
                                    setOf(
                                        CustomExpenseParticipationCommand(memberEmail("alice"), 100),
                                    ),
                            ),
                    ),
                )

                assertEquals(
                    Expense(
                        id = expenseId("expense-2"),
                        group = groupId("group-1"),
                        title = "Private purchase",
                        createdBy = memberEmail("alice"),
                        totalAmount = MoneyAmount.ofCents(100),
                        createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                        participations =
                            setOf(
                                savedExpenseParticipation(
                                    "alice",
                                    100,
                                    ExpenseParticipationStatus.Approved(
                                        Instant.parse("2026-04-03T10:00:00Z"),
                                    ),
                                ),
                            ),
                    ),
                    expenseRepository.findByIdAndGroup(expenseId("expense-2"), groupId("group-1")),
                )
            }
        }

        @Test
        fun `invoke should reject a creator who is not a group member before generating an expense id`() {
            val expenseRepository = InMemoryExpenseRepository()
            val useCase =
                useCase(
                    expenseRepository,
                    ExpenseIdGenerator { throw AssertionError("expense id should not be generated") },
                )

            assertThrows<GroupAccessDeniedException> {
                runTest {
                    useCase(
                        ProposeExpenseCommand(
                            group = groupId("group-1"),
                            title = "Unauthorized expense",
                            createdBy = memberEmail("outsider"),
                            totalAmountInCents = 100,
                            createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                            allocation = EqualSplitExpenseAllocationCommand(setOf(memberEmail("outsider"))),
                        ),
                    )
                }
            }

            runTest {
                assertEquals(null, expenseRepository.findByIdAndGroup(expenseId("expense-1"), groupId("group-1")))
            }
        }

        @Test
        fun `invoke should reject an equal split participant who is not a group member before generating an expense id`() {
            assertNonMemberParticipantIsRejected(
                EqualSplitExpenseAllocationCommand(
                    setOf(memberEmail("alice"), memberEmail("outsider")),
                ),
            )
        }

        @Test
        fun `invoke should identify the first non-member deterministically when several are supplied`() {
            assertNonMemberParticipantIsRejected(
                allocation =
                    EqualSplitExpenseAllocationCommand(
                        setOf(
                            memberEmail("alice"),
                            memberEmail("z-outsider"),
                            memberEmail("a-outsider"),
                        ),
                    ),
                expectedNonMember = memberEmail("a-outsider"),
            )
        }

        @Test
        fun `invoke should reject a non-member cap key before generating an expense id`() {
            assertNonMemberParticipantIsRejected(
                EqualSplitWithCapsExpenseAllocationCommand(
                    participants = setOf(memberEmail("alice"), memberEmail("bob")),
                    capsInCentsByMember = mapOf(memberEmail("outsider") to 20),
                ),
            )
        }

        @Test
        fun `invoke should reject a non-member cumulative tier participant before generating an expense id`() {
            assertNonMemberParticipantIsRejected(
                CumulativeTiersExpenseAllocationCommand(
                    tiers =
                        listOf(
                            CumulativeExpenseTierCommand(
                                upToAmountInCents = 50,
                                participants = setOf(memberEmail("alice")),
                            ),
                            CumulativeExpenseTierCommand(
                                upToAmountInCents = 100,
                                participants = setOf(memberEmail("alice"), memberEmail("outsider")),
                            ),
                        ),
                ),
            )
        }

        @Test
        fun `invoke should reject a custom participant who is not a group member before generating an expense id`() {
            assertNonMemberParticipantIsRejected(
                CustomExpenseAllocationCommand(
                    setOf(
                        CustomExpenseParticipationCommand(memberEmail("alice"), 50),
                        CustomExpenseParticipationCommand(memberEmail("outsider"), 50),
                    ),
                ),
            )
        }
    }

    private fun assertNonMemberParticipantIsRejected(
        allocation: ExpenseAllocationCommand,
        expectedNonMember: MemberEmail = memberEmail("outsider"),
    ) {
        val expenseRepository = InMemoryExpenseRepository()
        val useCase =
            useCase(
                expenseRepository,
                ExpenseIdGenerator { throw AssertionError("expense id should not be generated") },
            )

        val error =
            assertThrows<IllegalArgumentException> {
                runTest {
                    useCase(
                        ProposeExpenseCommand(
                            group = groupId("group-1"),
                            title = "Unauthorized expense",
                            createdBy = memberEmail("alice"),
                            totalAmountInCents = 100,
                            createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                            allocation = allocation,
                        ),
                    )
                }
            }

        assertEquals(
            "expense participant ${expectedNonMember.toPrimitive()} is not part of group ${groupId("group-1").toPrimitive()}",
            error.message,
        )
        runTest {
            assertEquals(null, expenseRepository.findByIdAndGroup(expenseId("expense-1"), groupId("group-1")))
        }
    }

    private fun useCase(
        expenseRepository: InMemoryExpenseRepository,
        expenseIdGenerator: ExpenseIdGenerator,
    ): ProposeExpenseUseCase =
        useCase(
            expenseIdGenerator = expenseIdGenerator,
            documentUploadIntentRepository = RecordingDocumentUploadIntentRepository(emptyList()),
            expenseProposalPersistence = RecordingExpenseProposalPersistence(expenseRepository),
        )

    private fun useCase(
        expenseIdGenerator: ExpenseIdGenerator,
        documentUploadIntentRepository: DocumentUploadIntentRepository,
        expenseProposalPersistence: ExpenseProposalPersistence,
    ): ProposeExpenseUseCase =
        ProposeExpenseUseCaseImpl(
            groupAccessPolicy = GroupAccessPolicy(InMemoryGroupRepository(listOf(group()))),
            expenseIdGenerator = expenseIdGenerator,
            documentUploadIntentRepository = documentUploadIntentRepository,
            expenseProposalPersistence = expenseProposalPersistence,
        )

    private fun documentedCommand(
        id: DocumentUploadIntentId,
        createdBy: MemberEmail = memberEmail("alice"),
    ): ProposeExpenseCommand =
        ProposeExpenseCommand(
            group = groupId("group-1"),
            title = "Documented repair",
            createdBy = createdBy,
            totalAmountInCents = 100,
            createdAt = CREATED_AT,
            allocation = EqualSplitExpenseAllocationCommand(setOf(createdBy)),
            supportingDocumentUploadIntents = setOf(id),
        )

    private fun readyIntent(
        seed: String,
        uploader: MemberEmail = memberEmail("alice"),
        group: GroupId = groupId("group-1"),
    ): DocumentUploadIntent = pendingIntent(seed, uploader, group).markReady(METADATA, CREATED_AT.minusSeconds(30))

    private fun pendingIntent(
        seed: String,
        uploader: MemberEmail = memberEmail("alice"),
        group: GroupId = groupId("group-1"),
    ): DocumentUploadIntent =
        DocumentUploadIntent.create(
            id = DocumentUploadIntentId(testUuid("$seed:propose-document")),
            group = group,
            uploader = uploader,
            storageKey = DocumentStorageKey.of("groups/${group.toPrimitive()}/documents/$seed.pdf"),
            fileName = DocumentFileName.of("$seed.pdf"),
            expectedMetadata = METADATA,
            createdAt = CREATED_AT.minusSeconds(60),
            expiresAt = CREATED_AT.plusSeconds(60),
        )

    private class RecordingDocumentUploadIntentRepository(
        intents: Iterable<DocumentUploadIntent>,
    ) : DocumentUploadIntentRepository {
        private val intentsById = intents.associateBy { it.id }.toMutableMap()

        override suspend fun persist(intent: DocumentUploadIntent) {
            intentsById[intent.id] = intent
        }

        override suspend fun persistAll(intents: List<DocumentUploadIntent>) {
            for (intent in intents) {
                persist(intent)
            }
        }

        override suspend fun findByIdAndGroup(
            id: DocumentUploadIntentId,
            group: GroupId,
        ): DocumentUploadIntent? = intentsById[id]?.takeIf { it.group == group }

        override suspend fun findPendingByIdAndGroupAndUploader(
            id: DocumentUploadIntentId,
            group: GroupId,
            uploader: MemberEmail,
        ): DocumentUploadIntent? =
            intentsById[id]?.takeIf { intent ->
                intent.group == group &&
                    intent.uploader == uploader &&
                    intent.status is tech.justdev.domain.document.entity.DocumentUploadIntentStatus.Pending
            }

        override suspend fun findReadyByIdsAndGroupAndUploader(
            ids: Set<DocumentUploadIntentId>,
            group: GroupId,
            uploader: MemberEmail,
        ): List<DocumentUploadIntent> =
            intentsById.values
                .asSequence()
                .filter { intent ->
                    intent.id in ids &&
                        intent.group == group &&
                        intent.uploader == uploader &&
                        intent.status is tech.justdev.domain.document.entity.DocumentUploadIntentStatus.Ready
                }.sortedBy { it.id.toPrimitive() }
                .toList()
    }

    private data class PersistedExpenseProposal(
        val expense: Expense,
        val consumedUploadIntents: List<DocumentUploadIntent>,
    )

    private class RecordingExpenseProposalPersistence(
        private val expenseRepository: ExpenseRepository? = null,
    ) : ExpenseProposalPersistence {
        val persisted = mutableListOf<PersistedExpenseProposal>()

        override suspend fun persist(
            expense: Expense,
            consumedUploadIntents: List<DocumentUploadIntent>,
        ) {
            persisted += PersistedExpenseProposal(expense, consumedUploadIntents)
            expenseRepository?.persist(expense)
        }
    }

    private fun group(): Group =
        Group
            .create(
                id = groupId("group-1"),
                createdBy = memberEmail("alice"),
                createdAt = Instant.parse("2026-04-01T10:00:00Z"),
            ).addMember(
                member = memberEmail("bob"),
                joinedAt = Instant.parse("2026-04-01T10:00:00Z"),
            ).addMember(
                member = memberEmail("carol"),
                joinedAt = Instant.parse("2026-04-01T10:00:00Z"),
            )

    private fun savedExpenseParticipation(
        memberId: String,
        amountInCents: Long,
        status: ExpenseParticipationStatus,
    ) = ExpenseParticipation(
        member = memberEmail(memberId),
        amount = MoneyAmount.ofCents(amountInCents),
        status = status,
    )

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-04-03T10:00:00Z")
        val METADATA =
            DocumentMetadata(
                mediaType = DocumentMediaType.of("application/pdf"),
                size = DocumentSize.ofBytes(512),
                checksum = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32))),
            )
    }

    data class UnavailableIntentCase(
        val stored: List<DocumentUploadIntent>,
        val requested: DocumentUploadIntentId,
    )
}
