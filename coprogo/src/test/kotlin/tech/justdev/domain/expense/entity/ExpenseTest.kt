package tech.justdev.domain.expense.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import tech.justdev.domain.document.entity.DocumentMetadata
import tech.justdev.domain.document.entity.DocumentUploadIntent
import tech.justdev.domain.document.entity.ExpenseSupportingDocument
import tech.justdev.domain.document.valueobject.DocumentFileName
import tech.justdev.domain.document.valueobject.DocumentMediaType
import tech.justdev.domain.document.valueobject.DocumentSha256
import tech.justdev.domain.document.valueobject.DocumentSize
import tech.justdev.domain.document.valueobject.DocumentStorageKey
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.expense.exception.ExpenseSupportingDocumentUnavailableException
import tech.justdev.domain.expense.valueobject.ExpenseParticipation
import tech.justdev.domain.expense.valueobject.ExpenseParticipationDecision
import tech.justdev.domain.expense.valueobject.ExpenseParticipationStatus
import tech.justdev.domain.expense.valueobject.ExpenseShare
import tech.justdev.domain.expense.valueobject.RefusalReason
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.testsupport.expenseId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.testUuid
import java.time.Instant
import java.util.Base64

class ExpenseTest {
    @Test
    fun `proposeEqualSplit should split remainder deterministically by member id`() {
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
                        ExpenseParticipation(
                            memberEmail("alice"),
                            MoneyAmount.ofCents(34),
                            ExpenseParticipationStatus.Approved(Instant.parse("2026-04-03T10:00:00Z")),
                        ),
                        ExpenseParticipation(memberEmail("bob"), MoneyAmount.ofCents(33), ExpenseParticipationStatus.Pending),
                        ExpenseParticipation(memberEmail("carol"), MoneyAmount.ofCents(33), ExpenseParticipationStatus.Pending),
                    ),
            ),
            Expense.proposeEqualSplit(
                id = expenseId("expense-1"),
                group = groupId("group-1"),
                title = "Boiler repair",
                createdBy = memberEmail("alice"),
                totalAmount = MoneyAmount.ofCents(100),
                createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                participants = setOf(memberEmail("carol"), memberEmail("alice"), memberEmail("bob")),
            ),
        )
    }

    @Test
    fun `proposeEqualSplit should fail when at least one participant would receive zero`() {
        assertThrows(IllegalArgumentException::class.java) {
            Expense.proposeEqualSplit(
                id = expenseId("expense-1"),
                group = groupId("group-1"),
                title = "Boiler repair",
                createdBy = memberEmail("alice"),
                totalAmount = MoneyAmount.ofCents(2),
                createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                participants = setOf(memberEmail("alice"), memberEmail("bob"), memberEmail("carol")),
            )
        }
    }

    @Nested
    inner class ProposeEqualSplitWithCaps {
        @Test
        fun `should redistribute a capped share equally`() {
            val expense =
                proposeEqualSplitWithCaps(
                    totalAmountInCents = 100,
                    participants = setOf("alice", "bob", "carol"),
                    caps = mapOf("bob" to 20),
                )

            assertEquals(
                mapOf(
                    memberEmail("alice") to MoneyAmount.ofCents(40),
                    memberEmail("bob") to MoneyAmount.ofCents(20),
                    memberEmail("carol") to MoneyAmount.ofCents(40),
                ),
                expense.amountsByMember(),
            )
        }

        @Test
        fun `should redistribute iteratively when another cap becomes active`() {
            val expense =
                proposeEqualSplitWithCaps(
                    totalAmountInCents = 100,
                    participants = setOf("dave", "carol", "bob", "alice"),
                    caps = mapOf("bob" to 10, "carol" to 28),
                )

            assertEquals(
                mapOf(
                    memberEmail("alice") to MoneyAmount.ofCents(31),
                    memberEmail("bob") to MoneyAmount.ofCents(10),
                    memberEmail("carol") to MoneyAmount.ofCents(28),
                    memberEmail("dave") to MoneyAmount.ofCents(31),
                ),
                expense.amountsByMember(),
            )
        }

        @Test
        fun `should assign the remaining cent deterministically by member email`() {
            val expense =
                proposeEqualSplitWithCaps(
                    totalAmountInCents = 101,
                    participants = setOf("carol", "bob", "alice"),
                    caps = mapOf("bob" to 20),
                )

            assertEquals(
                mapOf(
                    memberEmail("alice") to MoneyAmount.ofCents(41),
                    memberEmail("bob") to MoneyAmount.ofCents(20),
                    memberEmail("carol") to MoneyAmount.ofCents(40),
                ),
                expense.amountsByMember(),
            )
        }

        @Test
        fun `should leave equal shares unchanged when a cap is above them`() {
            val expense =
                proposeEqualSplitWithCaps(
                    totalAmountInCents = 100,
                    participants = setOf("carol", "bob", "alice"),
                    caps = mapOf("bob" to 40),
                )

            assertEquals(
                mapOf(
                    memberEmail("alice") to MoneyAmount.ofCents(34),
                    memberEmail("bob") to MoneyAmount.ofCents(33),
                    memberEmail("carol") to MoneyAmount.ofCents(33),
                ),
                expense.amountsByMember(),
            )
        }

        @Test
        fun `should leave an equal share unchanged when it exactly matches its cap`() {
            val expense =
                proposeEqualSplitWithCaps(
                    totalAmountInCents = 100,
                    participants = setOf("carol", "bob", "alice"),
                    caps = mapOf("bob" to 33),
                )

            assertEquals(
                mapOf(
                    memberEmail("alice") to MoneyAmount.ofCents(34),
                    memberEmail("bob") to MoneyAmount.ofCents(33),
                    memberEmail("carol") to MoneyAmount.ofCents(33),
                ),
                expense.amountsByMember(),
            )
        }

        @Test
        fun `should reject when every participant has a cap`() {
            assertThrows(IllegalArgumentException::class.java) {
                proposeEqualSplitWithCaps(
                    totalAmountInCents = 100,
                    participants = setOf("alice", "bob"),
                    caps = mapOf("alice" to 40, "bob" to 60),
                )
            }
        }

        @Test
        fun `should reject a cap for a non participant`() {
            assertThrows(IllegalArgumentException::class.java) {
                proposeEqualSplitWithCaps(
                    totalAmountInCents = 100,
                    participants = setOf("alice", "bob"),
                    caps = mapOf("carol" to 20),
                )
            }
        }

        @Test
        fun `should reject a zero cap`() {
            val error =
                assertThrows(IllegalArgumentException::class.java) {
                    proposeEqualSplitWithCaps(
                        totalAmountInCents = 100,
                        participants = setOf("alice", "bob"),
                        caps = mapOf("bob" to 0),
                    )
                }

            assertEquals("caps must be strictly positive", error.message)
        }
    }

    @Nested
    inner class ProposeCumulativeTiers {
        @Test
        fun `should add member contributions across successive tiers`() {
            val expense =
                proposeCumulativeTiers(
                    totalAmountInCents = 100,
                    tiers =
                        listOf(
                            CumulativeExpenseTier(
                                upTo = MoneyAmount.ofCents(60),
                                participants = setOf(memberEmail("carol"), memberEmail("alice"), memberEmail("bob")),
                            ),
                            CumulativeExpenseTier(
                                upTo = MoneyAmount.ofCents(80),
                                participants = setOf(memberEmail("bob"), memberEmail("alice")),
                            ),
                            CumulativeExpenseTier(
                                upTo = MoneyAmount.ofCents(100),
                                participants = setOf(memberEmail("alice")),
                            ),
                        ),
                )

            assertEquals(
                mapOf(
                    memberEmail("alice") to MoneyAmount.ofCents(50),
                    memberEmail("bob") to MoneyAmount.ofCents(30),
                    memberEmail("carol") to MoneyAmount.ofCents(20),
                ),
                expense.amountsByMember(),
            )
        }

        @Test
        fun `should assign remaining cents by member email in every tier`() {
            val expense =
                proposeCumulativeTiers(
                    totalAmountInCents = 101,
                    tiers =
                        listOf(
                            CumulativeExpenseTier(
                                upTo = MoneyAmount.ofCents(50),
                                participants = setOf(memberEmail("carol"), memberEmail("bob"), memberEmail("alice")),
                            ),
                            CumulativeExpenseTier(
                                upTo = MoneyAmount.ofCents(101),
                                participants = setOf(memberEmail("bob"), memberEmail("alice")),
                            ),
                        ),
                )

            assertEquals(
                mapOf(
                    memberEmail("alice") to MoneyAmount.ofCents(43),
                    memberEmail("bob") to MoneyAmount.ofCents(42),
                    memberEmail("carol") to MoneyAmount.ofCents(16),
                ),
                expense.amountsByMember(),
            )
        }

        @Test
        fun `should reject an empty tier list`() {
            assertThrows(IllegalArgumentException::class.java) {
                proposeCumulativeTiers(totalAmountInCents = 100, tiers = emptyList())
            }
        }

        @Test
        fun `should reject non increasing cumulative bounds`() {
            val error =
                assertThrows(IllegalArgumentException::class.java) {
                    proposeCumulativeTiers(
                        totalAmountInCents = 100,
                        tiers =
                            listOf(
                                CumulativeExpenseTier(MoneyAmount.ofCents(60), setOf(memberEmail("alice"))),
                                CumulativeExpenseTier(MoneyAmount.ofCents(60), setOf(memberEmail("alice"))),
                                CumulativeExpenseTier(MoneyAmount.ofCents(100), setOf(memberEmail("alice"))),
                            ),
                    )
                }

            assertEquals("cumulative tier bounds must be strictly increasing", error.message)
        }

        @Test
        fun `should reject when the last bound differs from the total amount`() {
            assertThrows(IllegalArgumentException::class.java) {
                proposeCumulativeTiers(
                    totalAmountInCents = 100,
                    tiers =
                        listOf(
                            CumulativeExpenseTier(MoneyAmount.ofCents(90), setOf(memberEmail("alice"))),
                        ),
                )
            }
        }

        @Test
        fun `should reject a tier without participants`() {
            assertThrows(IllegalArgumentException::class.java) {
                CumulativeExpenseTier(
                    upTo = MoneyAmount.ofCents(100),
                    participants = emptySet(),
                )
            }
        }

        @Test
        fun `should reject a zero cumulative bound`() {
            assertThrows(IllegalArgumentException::class.java) {
                CumulativeExpenseTier(
                    upTo = MoneyAmount.ZERO,
                    participants = setOf(memberEmail("alice")),
                )
            }
        }

        @Test
        fun `should reject a tier that would allocate zero to a participant`() {
            assertThrows(IllegalArgumentException::class.java) {
                proposeCumulativeTiers(
                    totalAmountInCents = 2,
                    tiers =
                        listOf(
                            CumulativeExpenseTier(
                                upTo = MoneyAmount.ofCents(2),
                                participants = setOf(memberEmail("alice"), memberEmail("bob"), memberEmail("carol")),
                            ),
                        ),
                )
            }
        }
    }

    @Nested
    inner class Propose {
        @Test
        fun `should auto approve creator participation and wait for other members`() {
            assertEquals(
                proposedExpense(),
                Expense.propose(
                    id = expenseId("expense-1"),
                    group = groupId("group-1"),
                    title = "Plumber invoice",
                    createdBy = memberEmail("alice"),
                    totalAmount = MoneyAmount.ofCents(100),
                    createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                    shares =
                        setOf(
                            ExpenseShare(memberEmail("alice"), MoneyAmount.ofCents(40)),
                            ExpenseShare(memberEmail("bob"), MoneyAmount.ofCents(60)),
                        ),
                ),
            )
        }

        @Test
        fun `should fail when creator does not participate`() {
            assertThrows(IllegalArgumentException::class.java) {
                Expense.propose(
                    id = expenseId("expense-1"),
                    group = groupId("group-1"),
                    title = "Plumber invoice",
                    createdBy = memberEmail("alice"),
                    totalAmount = MoneyAmount.ofCents(100),
                    createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                    shares =
                        setOf(
                            ExpenseShare(memberEmail("bob"), MoneyAmount.ofCents(100)),
                        ),
                )
            }
        }
    }

    @Nested
    inner class RecordParticipationDecision {
        @Test
        fun `recordParticipationDecision should accept expense when last pending member approves`() {
            val proposedExpense = proposedExpense()

            assertEquals(
                Expense(
                    id = expenseId("expense-1"),
                    group = groupId("group-1"),
                    title = "Plumber invoice",
                    createdBy = memberEmail("alice"),
                    totalAmount = MoneyAmount.ofCents(100),
                    createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                    participations =
                        setOf(
                            ExpenseParticipation(
                                memberEmail("alice"),
                                MoneyAmount.ofCents(40),
                                ExpenseParticipationStatus.Approved(Instant.parse("2026-04-03T10:00:00Z")),
                            ),
                            ExpenseParticipation(
                                memberEmail("bob"),
                                MoneyAmount.ofCents(60),
                                ExpenseParticipationStatus.Approved(Instant.parse("2026-04-03T12:00:00Z")),
                            ),
                        ),
                ),
                proposedExpense.recordParticipationDecision(
                    member = memberEmail("bob"),
                    decision = ExpenseParticipationDecision.APPROVE,
                    decidedAt = Instant.parse("2026-04-03T12:00:00Z"),
                ),
            )
        }

        @Test
        fun `recordParticipationDecision should invalidate expense when a member refuses`() {
            val proposedExpense = proposedExpense()

            assertEquals(
                Expense(
                    id = expenseId("expense-1"),
                    group = groupId("group-1"),
                    title = "Plumber invoice",
                    createdBy = memberEmail("alice"),
                    totalAmount = MoneyAmount.ofCents(100),
                    createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                    participations =
                        setOf(
                            ExpenseParticipation(
                                memberEmail("alice"),
                                MoneyAmount.ofCents(40),
                                ExpenseParticipationStatus.Approved(Instant.parse("2026-04-03T10:00:00Z")),
                            ),
                            ExpenseParticipation(
                                memberEmail("bob"),
                                MoneyAmount.ofCents(60),
                                ExpenseParticipationStatus.Refused(Instant.parse("2026-04-03T12:00:00Z")),
                            ),
                        ),
                ),
                proposedExpense.recordParticipationDecision(
                    member = memberEmail("bob"),
                    decision = ExpenseParticipationDecision.REFUSE,
                    decidedAt = Instant.parse("2026-04-03T12:00:00Z"),
                ),
            )
        }

        @Test
        fun `recordParticipationDecision should retain an optional refusal reason`() {
            val decidedAt = Instant.parse("2026-04-03T12:00:00Z")
            val reason = RefusalReason.of("This expense was not agreed")

            val refusedExpense =
                proposedExpense().recordParticipationDecision(
                    member = memberEmail("bob"),
                    decision = ExpenseParticipationDecision.REFUSE,
                    decidedAt = decidedAt,
                    reason = reason,
                )

            assertEquals(
                ExpenseParticipationStatus.Refused(decidedAt = decidedAt, reason = reason),
                refusedExpense.participations.single { participation -> participation.member == memberEmail("bob") }.status,
            )
        }

        @Test
        fun `recordParticipationDecision should reject a refusal reason on approval`() {
            val error =
                assertThrows(IllegalArgumentException::class.java) {
                    proposedExpense().recordParticipationDecision(
                        member = memberEmail("bob"),
                        decision = ExpenseParticipationDecision.APPROVE,
                        decidedAt = Instant.parse("2026-04-03T12:00:00Z"),
                        reason = RefusalReason.of("Only refusals may have a reason"),
                    )
                }

            assertEquals("refusal reason is only allowed for refusal decisions", error.message)
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class RequireSupportingDocumentChangeBy {
        private fun deniedDocumentChanges() = documentChangeDeniedCases()

        @Test
        fun `should allow the expense creator while the expense is proposed`() {
            proposedExpense().requireSupportingDocumentChangeBy(memberEmail("alice"))
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("deniedDocumentChanges")
        fun `should reject an unauthorized or terminal document change`(case: DocumentChangeDeniedCase) {
            val error =
                assertThrows(IllegalArgumentException::class.java) {
                    case.expense.requireSupportingDocumentChangeBy(case.requestedBy)
                }

            assertEquals(case.expectedMessage, error.message)
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class CanChangeSupportingDocumentsBy {
        private fun deniedDocumentChanges() = documentChangeDeniedCases()

        @Test
        fun `should report that the creator can change supporting documents while the expense is proposed`() {
            assertTrue(proposedExpense().canChangeSupportingDocumentsBy(memberEmail("alice")))
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("deniedDocumentChanges")
        fun `should report that another member or a terminal expense cannot change supporting documents`(case: DocumentChangeDeniedCase) {
            assertEquals(false, case.expense.canChangeSupportingDocumentsBy(case.requestedBy))
        }
    }

    @Nested
    inner class AttachSupportingDocuments {
        @Test
        fun `should attach every consumed upload intent when proposing an expense`() {
            val expense = proposedExpense()
            val first = consumedIntent("first")
            val second = consumedIntent("second")

            assertEquals(
                listOf(first.id, second.id),
                expense
                    .attachSupportingDocuments(listOf(first, second))
                    .supportingDocuments
                    .all
                    .map(ExpenseSupportingDocument::sourceUploadIntent),
            )
        }

        @Test
        fun `should reject attachments when the expense already has historical supporting documents`() {
            val expense = proposedExpense().attachSupportingDocuments(listOf(consumedIntent("first")))

            val error =
                assertThrows(IllegalArgumentException::class.java) {
                    expense.attachSupportingDocuments(listOf(consumedIntent("second")))
                }

            assertEquals("supporting documents can only be attached to an expense without documents", error.message)
        }

        @Test
        fun `should reject upload intents from another group`() {
            val error =
                assertThrows(IllegalArgumentException::class.java) {
                    proposedExpense().attachSupportingDocuments(listOf(consumedIntent("other", group = groupId("other-group"))))
                }

            assertEquals("expense and supporting document must belong to the same group", error.message)
        }

        @Test
        fun `should reject upload intents that are not consumed`() {
            val error =
                assertThrows(IllegalArgumentException::class.java) {
                    proposedExpense().attachSupportingDocuments(listOf(pendingIntent("pending")))
                }

            assertEquals("expense supporting document requires a consumed upload intent", error.message)
        }
    }

    @Nested
    inner class ReplaceSupportingDocument {
        @Test
        fun `should replace the selected document while retaining other current documents`() {
            val expense = proposedExpense().attachSupportingDocuments(listOf(consumedIntent("first"), consumedIntent("second")))
            val replacementIntent = consumedIntent("replacement")

            val replacement =
                expense.replaceSupportingDocument(
                    sourceUploadIntent = documentId("first"),
                    replacementIntent = replacementIntent,
                    requestedBy = memberEmail("alice"),
                )

            assertEquals(
                listOf(documentId("second"), documentId("replacement")),
                replacement.expense.supportingDocuments.current
                    .map(ExpenseSupportingDocument::sourceUploadIntent),
            )
            assertEquals(
                listOf(documentId("first"), documentId("second"), documentId("replacement")),
                replacement.expense.supportingDocuments.all
                    .map(ExpenseSupportingDocument::sourceUploadIntent),
            )
            assertEquals(documentId("first"), replacement.replacement.replacesSourceUploadIntent)
            assertEquals(documentId("replacement"), replacement.replacement.sourceUploadIntent)
        }

        @Test
        fun `should reject replacement of an historical document`() {
            val expense =
                proposedExpense()
                    .attachSupportingDocuments(listOf(consumedIntent("first")))
                    .replaceSupportingDocument(
                        sourceUploadIntent = documentId("first"),
                        replacementIntent = consumedIntent("replacement"),
                        requestedBy = memberEmail("alice"),
                    ).expense

            assertThrows(ExpenseSupportingDocumentUnavailableException::class.java) {
                expense.replaceSupportingDocument(
                    sourceUploadIntent = documentId("first"),
                    replacementIntent = consumedIntent("another-replacement"),
                    requestedBy = memberEmail("alice"),
                )
            }
        }

        @Test
        fun `should reject a replacement upload intent from another group`() {
            val expense = proposedExpense().attachSupportingDocuments(listOf(consumedIntent("first")))

            val error =
                assertThrows(IllegalArgumentException::class.java) {
                    expense.replaceSupportingDocument(
                        sourceUploadIntent = documentId("first"),
                        replacementIntent = consumedIntent("other", group = groupId("other-group")),
                        requestedBy = memberEmail("alice"),
                    )
                }

            assertEquals("expense and supporting document must belong to the same group", error.message)
        }

        @Test
        fun `should reject a replacement requested by a member other than the creator`() {
            val expense = proposedExpense().attachSupportingDocuments(listOf(consumedIntent("first")))

            val error =
                assertThrows(IllegalArgumentException::class.java) {
                    expense.replaceSupportingDocument(
                        sourceUploadIntent = documentId("first"),
                        replacementIntent = consumedIntent("replacement"),
                        requestedBy = memberEmail("bob"),
                    )
                }

            assertEquals("only the expense creator can change a supporting document", error.message)
        }
    }

    @Nested
    inner class DeleteSupportingDocument {
        @Test
        fun `should reject an absent current document`() {
            val error =
                assertThrows(ExpenseSupportingDocumentUnavailableException::class.java) {
                    proposedExpense().deleteSupportingDocument(
                        sourceUploadIntent = documentId("absent"),
                        requestedBy = memberEmail("alice"),
                        deletedAt = Instant.parse("2026-04-03T10:01:00Z"),
                    )
                }

            assertEquals("expense supporting document is unavailable", error.message)
        }

        @Test
        fun `should enforce the document change guard before looking for the selected document`() {
            val error =
                assertThrows(IllegalArgumentException::class.java) {
                    proposedExpense().deleteSupportingDocument(
                        sourceUploadIntent = documentId("absent"),
                        requestedBy = memberEmail("bob"),
                        deletedAt = Instant.parse("2026-04-03T10:01:00Z"),
                    )
                }

            assertEquals("only the expense creator can change a supporting document", error.message)
        }

        @Test
        fun `should delete only the selected document while retaining other current documents`() {
            val expense = proposedExpense().attachSupportingDocuments(listOf(consumedIntent("first"), consumedIntent("second")))

            val deletion =
                requireNotNull(
                    expense.deleteSupportingDocument(
                        sourceUploadIntent = documentId("first"),
                        requestedBy = memberEmail("alice"),
                        deletedAt = Instant.parse("2026-04-03T10:01:00Z"),
                    ),
                )

            assertEquals(
                listOf(documentId("second")),
                deletion.expense.supportingDocuments.current
                    .map(ExpenseSupportingDocument::sourceUploadIntent),
            )
            assertEquals(
                listOf(documentId("first"), documentId("second")),
                deletion.expense.supportingDocuments.all
                    .map(ExpenseSupportingDocument::sourceUploadIntent),
            )
            assertEquals(documentId("first"), deletion.deleted.sourceUploadIntent)
            assertEquals(memberEmail("alice"), deletion.deleted.deletion?.deletedBy)
        }

        @Test
        fun `should reject deletion of an historical document`() {
            val expense =
                proposedExpense()
                    .attachSupportingDocuments(listOf(consumedIntent("first")))
                    .replaceSupportingDocument(
                        sourceUploadIntent = documentId("first"),
                        replacementIntent = consumedIntent("replacement"),
                        requestedBy = memberEmail("alice"),
                    ).expense

            assertThrows(ExpenseSupportingDocumentUnavailableException::class.java) {
                expense.deleteSupportingDocument(
                    sourceUploadIntent = documentId("first"),
                    requestedBy = memberEmail("alice"),
                    deletedAt = Instant.parse("2026-04-03T10:01:00Z"),
                )
            }
        }
    }

    private fun proposedExpense(): Expense =
        Expense.propose(
            id = expenseId("expense-1"),
            group = groupId("group-1"),
            title = "Plumber invoice",
            createdBy = memberEmail("alice"),
            totalAmount = MoneyAmount.ofCents(100),
            createdAt = Instant.parse("2026-04-03T10:00:00Z"),
            shares =
                setOf(
                    ExpenseShare(memberEmail("alice"), MoneyAmount.ofCents(40)),
                    ExpenseShare(memberEmail("bob"), MoneyAmount.ofCents(60)),
                ),
        )

    private fun pendingIntent(
        seed: String,
        group: GroupId = groupId("group-1"),
    ): DocumentUploadIntent =
        DocumentUploadIntent
            .create(
                id = documentId(seed),
                group = group,
                uploader = memberEmail("alice"),
                storageKey = DocumentStorageKey.of("groups/group-1/documents/$seed.pdf"),
                fileName = DocumentFileName.of("$seed.pdf"),
                expectedMetadata = DOCUMENT_METADATA,
                createdAt = Instant.parse("2026-04-03T09:00:00Z"),
                expiresAt = Instant.parse("2026-04-03T11:00:00Z"),
            )

    private fun consumedIntent(
        seed: String,
        group: GroupId = groupId("group-1"),
    ): DocumentUploadIntent =
        pendingIntent(seed, group)
            .markReady(DOCUMENT_METADATA, Instant.parse("2026-04-03T09:30:00Z"))
            .consume(Instant.parse("2026-04-03T09:45:00Z"))

    private fun documentId(seed: String): DocumentUploadIntentId = DocumentUploadIntentId(testUuid("doc-$seed"))

    private fun documentChangeDeniedCases() =
        listOf(
            DocumentChangeDeniedCase(
                expense = proposedExpense(),
                requestedBy = memberEmail("bob"),
                expectedMessage = "only the expense creator can change a supporting document",
            ),
            DocumentChangeDeniedCase(
                expense =
                    proposedExpense().recordParticipationDecision(
                        member = memberEmail("bob"),
                        decision = ExpenseParticipationDecision.APPROVE,
                        decidedAt = Instant.parse("2026-04-03T12:00:00Z"),
                    ),
                requestedBy = memberEmail("alice"),
                expectedMessage = "supporting documents can only be changed while the expense is proposed",
            ),
            DocumentChangeDeniedCase(
                expense =
                    proposedExpense().recordParticipationDecision(
                        member = memberEmail("bob"),
                        decision = ExpenseParticipationDecision.REFUSE,
                        decidedAt = Instant.parse("2026-04-03T12:00:00Z"),
                    ),
                requestedBy = memberEmail("alice"),
                expectedMessage = "supporting documents can only be changed while the expense is proposed",
            ),
        )

    private fun proposeEqualSplitWithCaps(
        totalAmountInCents: Long,
        participants: Set<String>,
        caps: Map<String, Long>,
    ): Expense =
        Expense.proposeEqualSplitWithCaps(
            id = expenseId("expense-with-caps"),
            group = groupId("group-1"),
            title = "Boiler repair",
            createdBy = memberEmail("alice"),
            totalAmount = MoneyAmount.ofCents(totalAmountInCents),
            createdAt = Instant.parse("2026-04-03T10:00:00Z"),
            participants = participants.map(::memberEmail).toSet(),
            capsByMember = caps.mapKeys { (member) -> memberEmail(member) }.mapValues { (_, amount) -> MoneyAmount.ofCents(amount) },
        )

    private fun proposeCumulativeTiers(
        totalAmountInCents: Long,
        tiers: List<CumulativeExpenseTier>,
    ): Expense =
        Expense.proposeCumulativeTiers(
            id = expenseId("expense-with-cumulative-tiers"),
            group = groupId("group-1"),
            title = "Boiler repair",
            createdBy = memberEmail("alice"),
            totalAmount = MoneyAmount.ofCents(totalAmountInCents),
            createdAt = Instant.parse("2026-04-03T10:00:00Z"),
            tiers = tiers,
        )

    private fun Expense.amountsByMember() = participations.associate { participation -> participation.member to participation.amount }

    private companion object {
        val DOCUMENT_METADATA =
            DocumentMetadata(
                mediaType = DocumentMediaType.of("application/pdf"),
                size = DocumentSize.ofBytes(128),
                checksum = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32))),
            )
    }

    data class DocumentChangeDeniedCase(
        val expense: Expense,
        val requestedBy: MemberEmail,
        val expectedMessage: String,
    )
}
