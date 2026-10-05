package tech.justdev.application.reimbursement

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
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

class GetReimbursementUseCaseTest {
    @Nested
    inner class Invoke {
        @Test
        fun `should verify membership before accessing reimbursement`() {
            val interactions = mutableListOf<String>()

            assertThrows<GroupAccessDeniedException> {
                runTest {
                    useCase(
                        reimbursementRepository = FailingReimbursementRepository(),
                        groupRepository = DenyingGroupRepository(interactions),
                    )(query(requestedBy = OUTSIDER))
                }
            }

            assertEquals(listOf("membership"), interactions)
        }

        @Test
        fun `should use the requested group and report a missing reimbursement uniformly`() {
            val repository = RecordingReimbursementRepository(null)

            val error = assertThrows<ReimbursementNotFoundException> { runTest { useCase(repository)(query()) } }

            assertEquals(REIMBURSEMENT, error.reimbursement)
            assertEquals(GROUP, error.group)
            assertEquals(listOf(GROUP), repository.queriedGroups)
        }

        @Test
        fun `should return the complete reimbursement`() =
            runTest {
                val reimbursement = reimbursement()

                val result = useCase(RecordingReimbursementRepository(reimbursement))(query())

                assertSame(reimbursement, result)
            }
    }

    private fun useCase(
        reimbursementRepository: ReimbursementRepository,
        groupRepository: GroupRepository = GroupRepositoryFake(),
    ): GetReimbursementUseCase =
        GetReimbursementUseCaseImpl(
            GroupAccessPolicy(groupRepository),
            reimbursementRepository,
        )

    private fun query(requestedBy: MemberEmail = REQUESTER) = GetReimbursementQuery(GROUP, REIMBURSEMENT, requestedBy)

    private fun reimbursement(): Reimbursement =
        Reimbursement.restore(
            REIMBURSEMENT,
            GROUP,
            PAID_BY,
            REQUESTER,
            MoneyAmount.ofCents(1_200),
            REIMBURSED_AT,
            PAID_BY,
            DECLARED_AT,
            ReimbursementStatus.Rejected(DECIDED_AT, ReimbursementRejectionReason.of("Duplicate")),
            listOf(
                ReimbursementSupportingDocument.restore(
                    INTENT,
                    GROUP,
                    PAID_BY,
                    DocumentStorageKey.of("documents/$INTENT"),
                    DocumentFileName.of("receipt.pdf"),
                    DocumentMetadata(DocumentMediaType.of("application/pdf"), DocumentSize.ofBytes(256), CHECKSUM),
                    ATTACHED_AT,
                ),
            ),
        )

    private class GroupRepositoryFake : GroupRepository {
        override suspend fun findById(id: GroupId): Group? = Group.create(GROUP, REQUESTER, DECLARED_AT).addMember(PAID_BY, DECLARED_AT)

        override suspend fun persist(group: Group) = Unit
    }

    private class DenyingGroupRepository(
        private val interactions: MutableList<String>,
    ) : GroupRepository {
        override suspend fun findById(id: GroupId): Group? =
            Group
                .create(GROUP, REQUESTER, DECLARED_AT)
                .addMember(PAID_BY, DECLARED_AT)
                .also { interactions += "membership" }

        override suspend fun persist(group: Group) = Unit
    }

    private class RecordingReimbursementRepository(
        private val result: Reimbursement?,
    ) : ReimbursementRepository {
        val queriedGroups = mutableListOf<GroupId>()

        override suspend fun findByIdAndGroup(
            id: ReimbursementId,
            group: GroupId,
        ): Reimbursement? =
            result
                ?.takeIf {
                    it.id == id &&
                        it.group == group
                }.also { queriedGroups += group }

        override suspend fun findByGroup(group: GroupId): List<Reimbursement> = error("not used")

        override suspend fun persist(reimbursement: Reimbursement) = error("not used")
    }

    private class FailingReimbursementRepository : ReimbursementRepository {
        override suspend fun findByIdAndGroup(
            id: ReimbursementId,
            group: GroupId,
        ): Reimbursement? = error("reimbursement repository must not be accessed")

        override suspend fun findByGroup(group: GroupId): List<Reimbursement> = error("reimbursement repository must not be accessed")

        override suspend fun persist(reimbursement: Reimbursement) = error("reimbursement repository must not be accessed")
    }

    private companion object {
        val GROUP = groupId("get-reimbursement")
        val REIMBURSEMENT = ReimbursementId(testUuid("get-reimbursement"))
        val INTENT = DocumentUploadIntentId(testUuid("get-reimbursement-document"))
        val REQUESTER: MemberEmail = memberEmail("receiver")
        val PAID_BY: MemberEmail = memberEmail("payer")
        val OUTSIDER: MemberEmail = memberEmail("outsider")
        val REIMBURSED_AT = Instant.parse("2026-10-02T09:00:00Z")
        val DECLARED_AT = Instant.parse("2026-10-02T10:00:00Z")
        val DECIDED_AT = Instant.parse("2026-10-02T11:00:00Z")
        val ATTACHED_AT = Instant.parse("2026-10-02T10:00:01Z")
        val CHECKSUM = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32)))
    }
}
