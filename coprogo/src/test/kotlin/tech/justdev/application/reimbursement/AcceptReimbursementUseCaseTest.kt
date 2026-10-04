package tech.justdev.application.reimbursement

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.application.group.GroupAccessDeniedException
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.application.support.InMemoryAcceptedReimbursementPersistence
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
import tech.justdev.domain.reimbursement.valueobject.ReimbursementStatus
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.testUuid
import java.time.Instant
import java.util.Base64

class AcceptReimbursementUseCaseTest {
    @Nested
    inner class Invoke {
        @Test
        fun `should persist the accepted reimbursement`() =
            runTest {
                val repository = InMemoryReimbursementRepository(listOf(pendingReimbursement()))
                val persistence = InMemoryAcceptedReimbursementPersistence()

                useCase(repository, persistence)(command())

                assertEquals(
                    listOf(ReimbursementStatus.Accepted(ACCEPTED_AT)),
                    persistence.findAll().map(Reimbursement::status),
                )
            }

        @Test
        fun `should verify group membership before reading the reimbursement`() {
            assertThrows<GroupAccessDeniedException> {
                runTest {
                    useCase(FailingReimbursementRepository, InMemoryAcceptedReimbursementPersistence())(
                        command(acceptedBy = "outsider"),
                    )
                }
            }
        }

        @Test
        fun `should expose the same not found error for a missing or cross-group reimbursement`() {
            val repositories =
                listOf(
                    InMemoryReimbursementRepository(),
                    InMemoryReimbursementRepository(listOf(pendingReimbursement(OTHER_GROUP))),
                )

            val errors = listOf(unavailableError(repositories[0]), unavailableError(repositories[1]))

            assertEquals(
                listOf(
                    "reimbursement ${ID.toPrimitive()} was not found in group ${GROUP.toPrimitive()}",
                    "reimbursement ${ID.toPrimitive()} was not found in group ${GROUP.toPrimitive()}",
                ),
                errors,
            )
        }

        private fun unavailableError(repository: ReimbursementRepository): String? =
            assertThrows<ReimbursementNotFoundException> {
                runTest {
                    useCase(repository, InMemoryAcceptedReimbursementPersistence())(command())
                }
            }.message
    }

    private fun useCase(
        repository: ReimbursementRepository,
        persistence: AcceptedReimbursementPersistence,
    ): AcceptReimbursementUseCase =
        AcceptReimbursementUseCaseImpl(
            groupAccessPolicy = GroupAccessPolicy(InMemoryGroupRepository(listOf(group()))),
            reimbursementRepository = repository,
            acceptedReimbursementPersistence = persistence,
        )

    private fun command(acceptedBy: String = "alice") =
        AcceptReimbursementCommand(
            group = GROUP,
            reimbursement = ID,
            acceptedBy = memberEmail(acceptedBy),
            acceptedAt = ACCEPTED_AT,
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
            sourceUploadIntent = DocumentUploadIntentId(testUuid("document:accept-reimbursement")),
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
        val GROUP = groupId("accept-reimbursement")
        val OTHER_GROUP = groupId("other-accept-reimbursement")
        val ID = ReimbursementId(testUuid("reimbursement:accept"))
        val REIMBURSED_AT: Instant = Instant.parse("2026-04-02T10:00:00Z")
        val DECLARED_AT: Instant = Instant.parse("2026-04-03T10:00:00Z")
        val ACCEPTED_AT: Instant = Instant.parse("2026-04-03T11:00:00Z")
        val METADATA =
            DocumentMetadata(
                mediaType = DocumentMediaType.of("application/pdf"),
                size = DocumentSize.ofBytes(512),
                checksum = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32))),
            )
    }
}
