package tech.justdev.application.reimbursement

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
import tech.justdev.application.support.InMemoryGroupRepository
import tech.justdev.application.support.InMemoryReimbursementRepository
import tech.justdev.domain.document.entity.DocumentMetadata
import tech.justdev.domain.document.valueobject.DocumentFileName
import tech.justdev.domain.document.valueobject.DocumentMediaType
import tech.justdev.domain.document.valueobject.DocumentSha256
import tech.justdev.domain.document.valueobject.DocumentSize
import tech.justdev.domain.document.valueobject.DocumentStorageKey
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.reimbursement.entity.Reimbursement
import tech.justdev.domain.reimbursement.entity.ReimbursementSupportingDocument
import tech.justdev.domain.reimbursement.repository.ReimbursementRepository
import tech.justdev.domain.reimbursement.valueobject.ReimbursementId
import tech.justdev.domain.reimbursement.valueobject.ReimbursementRejectionReason
import tech.justdev.domain.reimbursement.valueobject.ReimbursementStatus
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.testUuid
import java.time.Instant
import java.util.Base64

class RejectReimbursementUseCaseTest {
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class Invoke {
        private fun unavailableReimbursementCases(): List<UnavailableReimbursementCase> =
            listOf(
                UnavailableReimbursementCase(InMemoryReimbursementRepository()),
                UnavailableReimbursementCase(
                    InMemoryReimbursementRepository(listOf(pendingReimbursement(group = OTHER_GROUP))),
                ),
            )

        private fun invalidRejectionCases(): List<InvalidRejectionCase> {
            val pending = pendingReimbursement()
            return listOf(
                InvalidRejectionCase(
                    stored = pending,
                    command = command(rejectedBy = "charlie"),
                    expectedMessage = "only the reimbursement receiver can review it",
                ),
                InvalidRejectionCase(
                    stored = pending.accept(memberEmail("alice"), DECLARED_AT.plusSeconds(1)),
                    command = command(),
                    expectedMessage = "reimbursement is not awaiting review",
                ),
                InvalidRejectionCase(
                    stored = pending,
                    command = command(rejectedAt = DECLARED_AT.minusNanos(1)),
                    expectedMessage = "reimbursement review decision must not precede its declaration",
                ),
            )
        }

        @Test
        fun `should persist the complete reimbursement rejected by its receiver`() =
            runTest {
                val repository = InMemoryReimbursementRepository(listOf(pendingReimbursement()))
                val reason = ReimbursementRejectionReason.of("The document does not match the payment")

                useCase(repository)(command(reason = reason))

                assertEquals(
                    listOf(
                        ReimbursementSnapshot(
                            id = ID,
                            group = GROUP,
                            paidBy = "bob@example.com",
                            receivedBy = "alice@example.com",
                            amountInCents = 4_200,
                            reimbursedAt = REIMBURSED_AT,
                            declaredBy = "bob@example.com",
                            declaredAt = DECLARED_AT,
                            status = ReimbursementStatus.Rejected(REJECTED_AT, reason),
                            supportingDocumentUploadIntents = listOf(DOCUMENT_ID),
                        ),
                    ),
                    repository.findByGroup(GROUP).map { reimbursement -> reimbursement.toSnapshot() },
                )
            }

        @Test
        fun `should verify group membership before reading the reimbursement`() {
            assertThrows<GroupAccessDeniedException> {
                runTest {
                    useCase(FailingReimbursementRepository)(command(rejectedBy = "outsider"))
                }
            }
        }

        @ParameterizedTest(name = "{index}")
        @MethodSource("unavailableReimbursementCases")
        fun `should expose the same not found error for a missing or cross-group reimbursement`(case: UnavailableReimbursementCase) {
            val error =
                assertThrows<ReimbursementNotFoundException> {
                    runTest { useCase(case.repository)(command()) }
                }

            assertEquals(
                "reimbursement ${ID.toPrimitive()} was not found in group ${GROUP.toPrimitive()}",
                error.message,
            )
            assertEquals(case.initialStatuses, case.repository.findAll().map(Reimbursement::status))
        }

        @ParameterizedTest(name = "{index}")
        @MethodSource("invalidRejectionCases")
        fun `should preserve the reimbursement when the domain rejects the decision`(case: InvalidRejectionCase) {
            val repository = InMemoryReimbursementRepository(listOf(case.stored))

            val error =
                assertThrows<IllegalArgumentException> {
                    runTest { useCase(repository)(case.command) }
                }

            assertEquals(case.expectedMessage, error.message)
            assertEquals(listOf(case.stored.status), repository.findAll().map(Reimbursement::status))
        }
    }

    private fun useCase(repository: ReimbursementRepository): RejectReimbursementUseCase =
        RejectReimbursementUseCaseImpl(
            groupAccessPolicy = GroupAccessPolicy(InMemoryGroupRepository(listOf(group()))),
            reimbursementRepository = repository,
        )

    private fun command(
        rejectedBy: String = "alice",
        rejectedAt: Instant = REJECTED_AT,
        reason: ReimbursementRejectionReason? = null,
    ) = RejectReimbursementCommand(
        group = GROUP,
        reimbursement = ID,
        rejectedBy = memberEmail(rejectedBy),
        rejectedAt = rejectedAt,
        reason = reason,
    )

    private fun pendingReimbursement(group: GroupId = GROUP): Reimbursement =
        Reimbursement.restore(
            id = ID,
            group = group,
            paidBy = memberEmail("bob"),
            receivedBy = memberEmail("alice"),
            amount = MoneyAmount.ofCents(4_200),
            reimbursedAt = REIMBURSED_AT,
            declaredBy = memberEmail("bob"),
            declaredAt = DECLARED_AT,
            status = ReimbursementStatus.PendingReview,
            supportingDocuments = listOf(supportingDocument(group)),
        )

    private fun supportingDocument(group: GroupId): ReimbursementSupportingDocument =
        ReimbursementSupportingDocument.restore(
            sourceUploadIntent = DOCUMENT_ID,
            group = group,
            uploader = memberEmail("bob"),
            storageKey = DocumentStorageKey.of("groups/${group.toPrimitive()}/documents/reimbursement.pdf"),
            fileName = DocumentFileName.of("reimbursement.pdf"),
            metadata = METADATA,
            attachedAt = DECLARED_AT,
        )

    private fun group(): Group =
        Group
            .create(
                id = GROUP,
                createdBy = memberEmail("alice"),
                createdAt = Instant.parse("2026-04-01T08:00:00Z"),
            ).addMember(
                member = memberEmail("bob"),
                joinedAt = Instant.parse("2026-04-01T09:00:00Z"),
            ).addMember(
                member = memberEmail("charlie"),
                joinedAt = Instant.parse("2026-04-01T10:00:00Z"),
            )

    private fun Reimbursement.toSnapshot() =
        ReimbursementSnapshot(
            id = id,
            group = group,
            paidBy = paidBy.toPrimitive(),
            receivedBy = receivedBy.toPrimitive(),
            amountInCents = amount.inCents(),
            reimbursedAt = reimbursedAt,
            declaredBy = declaredBy.toPrimitive(),
            declaredAt = declaredAt,
            status = status,
            supportingDocumentUploadIntents = supportingDocuments.map(ReimbursementSupportingDocument::sourceUploadIntent),
        )

    data class UnavailableReimbursementCase(
        val repository: InMemoryReimbursementRepository,
    ) {
        val initialStatuses: List<ReimbursementStatus> = repository.findAll().map(Reimbursement::status)
    }

    data class InvalidRejectionCase(
        val stored: Reimbursement,
        val command: RejectReimbursementCommand,
        val expectedMessage: String,
    )

    private data class ReimbursementSnapshot(
        val id: ReimbursementId,
        val group: GroupId,
        val paidBy: String,
        val receivedBy: String,
        val amountInCents: Long,
        val reimbursedAt: Instant,
        val declaredBy: String,
        val declaredAt: Instant,
        val status: ReimbursementStatus,
        val supportingDocumentUploadIntents: List<DocumentUploadIntentId>,
    )

    private object FailingReimbursementRepository : ReimbursementRepository {
        override suspend fun findByIdAndGroup(
            id: ReimbursementId,
            group: GroupId,
        ): Reimbursement = throw AssertionError("reimbursement should not be read")

        override suspend fun findByGroup(group: GroupId): List<Reimbursement> = throw AssertionError("reimbursements should not be read")

        override suspend fun persist(reimbursement: Reimbursement): Nothing = throw AssertionError("reimbursement should not be persisted")
    }

    private companion object {
        val GROUP = groupId("reject-reimbursement")
        val OTHER_GROUP = groupId("other-reject-reimbursement")
        val ID = ReimbursementId(testUuid("reimbursement:reject"))
        val DOCUMENT_ID = DocumentUploadIntentId(testUuid("document:reject-reimbursement"))
        val REIMBURSED_AT: Instant = Instant.parse("2026-04-02T10:00:00Z")
        val DECLARED_AT: Instant = Instant.parse("2026-04-03T10:00:00Z")
        val REJECTED_AT: Instant = Instant.parse("2026-04-03T11:00:00Z")
        val METADATA =
            DocumentMetadata(
                mediaType = DocumentMediaType.of("application/pdf"),
                size = DocumentSize.ofBytes(512),
                checksum = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32))),
            )
    }
}
