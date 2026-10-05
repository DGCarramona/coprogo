package tech.justdev.application.reimbursement

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.application.group.GroupAccessDeniedException
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.domain.document.entity.DocumentMetadata
import tech.justdev.domain.document.valueobject.DocumentFileName
import tech.justdev.domain.document.valueobject.DocumentMediaType
import tech.justdev.domain.document.valueobject.DocumentSha256
import tech.justdev.domain.document.valueobject.DocumentSize
import tech.justdev.domain.document.valueobject.DocumentStorageKey
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.valueobject.MemberEmail
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

class ListReimbursementsUseCaseTest {
    @Nested
    inner class Invoke {
        @Test
        fun `should verify membership before accessing reimbursements`() {
            val interactions = mutableListOf<String>()

            assertThrows<GroupAccessDeniedException> {
                runTest {
                    useCase(RecordingGroupRepository(interactions, group()), FailingReimbursementRepository())(
                        query(requestedBy = OUTSIDER),
                    )
                }
            }

            assertEquals(listOf("membership"), interactions)
        }

        @Test
        fun `should return complete reimbursements ordered by reimbursement date then identifier`() =
            runTest {
                val sameDateFirst =
                    reimbursement(
                        id = REIMBURSEMENT_A,
                        reimbursedAt = REIMBURSED_AT,
                        status = ReimbursementStatus.PendingReview,
                        documents = listOf(document()),
                    )
                val sameDateSecond =
                    reimbursement(id = REIMBURSEMENT_B, reimbursedAt = REIMBURSED_AT, status = ReimbursementStatus.Accepted(DECIDED_AT))
                val older =
                    reimbursement(
                        id = REIMBURSEMENT_C,
                        reimbursedAt = REIMBURSED_AT.minusSeconds(1),
                        status = ReimbursementStatus.Rejected(DECIDED_AT, ReimbursementRejectionReason.of("Duplicate")),
                        documents = listOf(document()),
                    )
                val reimbursementRepository = RecordingReimbursementRepository(listOf(older, sameDateFirst, sameDateSecond))
                val result =
                    useCase(
                        RecordingGroupRepository(mutableListOf(), group()),
                        reimbursementRepository,
                    )(query())

                assertEquals(listOf(sameDateSecond, sameDateFirst, older), result)
                assertEquals(listOf(GROUP), reimbursementRepository.groupQueries)
            }
    }

    private fun useCase(
        groupRepository: GroupRepository,
        reimbursementRepository: ReimbursementRepository,
    ): ListReimbursementsUseCase = ListReimbursementsUseCaseImpl(GroupAccessPolicy(groupRepository), reimbursementRepository)

    private fun query(requestedBy: MemberEmail = REQUESTER): ListReimbursementsQuery = ListReimbursementsQuery(GROUP, requestedBy)

    private fun group(): Group = Group.create(GROUP, REQUESTER, CREATED_AT).addMember(PAID_BY, CREATED_AT)

    private fun reimbursement(
        id: ReimbursementId,
        reimbursedAt: Instant,
        status: ReimbursementStatus,
        documents: List<ReimbursementSupportingDocument> = emptyList(),
    ): Reimbursement =
        Reimbursement.restore(
            id,
            GROUP,
            PAID_BY,
            REQUESTER,
            MoneyAmount.ofCents(1_200),
            reimbursedAt,
            if (documents.isEmpty()) REQUESTER else PAID_BY,
            DECLARED_AT,
            status,
            documents,
        )

    private fun document(): ReimbursementSupportingDocument =
        ReimbursementSupportingDocument.restore(
            SOURCE_UPLOAD_INTENT,
            GROUP,
            PAID_BY,
            DocumentStorageKey.of("documents/$SOURCE_UPLOAD_INTENT"),
            DocumentFileName.of("receipt.pdf"),
            DocumentMetadata(DocumentMediaType.of("application/pdf"), DocumentSize.ofBytes(256), CHECKSUM),
            ATTACHED_AT,
        )

    private class RecordingGroupRepository(
        private val interactions: MutableList<String>,
        private val group: Group?,
    ) : GroupRepository {
        override suspend fun findById(id: GroupId): Group? = group?.takeIf { it.id == id }.also { interactions += "membership" }

        override suspend fun persist(group: Group) = Unit
    }

    private class RecordingReimbursementRepository(
        private val reimbursements: List<Reimbursement>,
    ) : ReimbursementRepository {
        val groupQueries = mutableListOf<GroupId>()

        override suspend fun findByIdAndGroup(
            id: ReimbursementId,
            group: GroupId,
        ): Reimbursement? = reimbursements.singleOrNull { reimbursement -> reimbursement.id == id && reimbursement.group == group }

        override suspend fun findByGroup(group: GroupId): List<Reimbursement> = reimbursements.also { groupQueries += group }

        override suspend fun persist(reimbursement: Reimbursement) = error("not used")
    }

    private class FailingReimbursementRepository : ReimbursementRepository {
        override suspend fun findByIdAndGroup(
            id: ReimbursementId,
            group: GroupId,
        ): Reimbursement? = error("repository must not be accessed")

        override suspend fun findByGroup(group: GroupId): List<Reimbursement> = error("repository must not be accessed")

        override suspend fun persist(reimbursement: Reimbursement) = error("repository must not be accessed")
    }

    private companion object {
        val GROUP: GroupId = groupId("list-reimbursements")
        val REQUESTER: MemberEmail = memberEmail("receiver")
        val PAID_BY: MemberEmail = memberEmail("payer")
        val OUTSIDER: MemberEmail = memberEmail("outsider")
        val REIMBURSEMENT_A = ReimbursementId(testUuid("reimbursement-a"))
        val REIMBURSEMENT_B = ReimbursementId(testUuid("reimbursement-b"))
        val REIMBURSEMENT_C = ReimbursementId(testUuid("reimbursement-c"))
        val SOURCE_UPLOAD_INTENT = DocumentUploadIntentId(testUuid("receipt"))
        val CREATED_AT: Instant = Instant.parse("2026-10-01T09:00:00Z")
        val REIMBURSED_AT: Instant = Instant.parse("2026-10-02T09:00:00Z")
        val DECLARED_AT: Instant = Instant.parse("2026-10-02T10:00:00Z")
        val DECIDED_AT: Instant = Instant.parse("2026-10-02T11:00:00Z")
        val ATTACHED_AT: Instant = Instant.parse("2026-10-02T10:00:01Z")
        val CHECKSUM: DocumentSha256 = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32)))
    }
}
