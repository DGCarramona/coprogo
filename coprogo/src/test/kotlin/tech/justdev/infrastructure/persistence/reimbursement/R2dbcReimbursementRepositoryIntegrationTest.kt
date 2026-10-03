package tech.justdev.infrastructure.persistence.reimbursement

import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.domain.document.entity.DocumentMetadata
import tech.justdev.domain.document.entity.DocumentUploadIntent
import tech.justdev.domain.document.repository.DocumentUploadIntentRepository
import tech.justdev.domain.document.valueobject.DocumentFileName
import tech.justdev.domain.document.valueobject.DocumentMediaType
import tech.justdev.domain.document.valueobject.DocumentSha256
import tech.justdev.domain.document.valueobject.DocumentSize
import tech.justdev.domain.document.valueobject.DocumentStorageKey
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.reimbursement.entity.Reimbursement
import tech.justdev.domain.reimbursement.entity.ReimbursementSupportingDocument
import tech.justdev.domain.reimbursement.repository.ReimbursementRepository
import tech.justdev.domain.reimbursement.valueobject.ReimbursementId
import tech.justdev.domain.reimbursement.valueobject.ReimbursementRejectionReason
import tech.justdev.domain.reimbursement.valueobject.ReimbursementStatus
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.testsupport.PostgresMicronautTest
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.UUID

@PostgresMicronautTest
class R2dbcReimbursementRepositoryIntegrationTest {
    @Inject
    lateinit var repository: ReimbursementRepository

    @Inject
    lateinit var memberRepository: MemberRepository

    @Inject
    lateinit var groupRepository: GroupRepository

    @Inject
    lateinit var documentUploadIntentRepository: DocumentUploadIntentRepository

    @Nested
    inner class Persist {
        @Test
        fun `should idempotently round trip a direct reimbursement`() =
            runTest {
                val reimbursement = directReimbursement("direct")

                repository.persist(reimbursement)
                repository.persist(reimbursement)

                assertEquals(
                    reimbursement.toSnapshot(),
                    repository.findByIdAndGroup(reimbursement.id, reimbursement.group)?.toSnapshot(),
                )
            }

        @Test
        fun `should round trip every documented reimbursement status and its document snapshots`() =
            runTest {
                val pending = documentedReimbursement("pending", documentCount = 2)
                val accepted = documentedReimbursement("accepted").accept(receiver("accepted"), DECIDED_AT)
                val rejectedWithReason =
                    documentedReimbursement("rejected-reason").reject(
                        reviewedBy = receiver("rejected-reason"),
                        rejectedAt = DECIDED_AT,
                        reason = ReimbursementRejectionReason.of("Le justificatif ne correspond pas"),
                    )
                val rejectedWithoutReason =
                    documentedReimbursement("rejected-without-reason").reject(
                        reviewedBy = receiver("rejected-without-reason"),
                        rejectedAt = DECIDED_AT,
                    )
                val reimbursements = listOf(pending, accepted, rejectedWithReason, rejectedWithoutReason)

                reimbursements.forEach { reimbursement ->
                    repository.persist(reimbursement)
                    repository.persist(reimbursement)
                }

                assertEquals(
                    reimbursements.map { reimbursement -> reimbursement.toSnapshot() },
                    reimbursements.map { reimbursement ->
                        repository.findByIdAndGroup(reimbursement.id, reimbursement.group)?.toSnapshot()
                    },
                )
            }

        @Test
        fun `should append a review decision to an existing pending reimbursement`() =
            runTest {
                val pending = documentedReimbursement("transition")
                val accepted = pending.accept(receiver("transition"), DECIDED_AT)
                repository.persist(pending)

                repository.persist(accepted)

                assertEquals(
                    accepted.toSnapshot(),
                    repository.findByIdAndGroup(accepted.id, accepted.group)?.toSnapshot(),
                )
            }

        @Test
        fun `should reject conflicting immutable data and terminal decisions without changing history`() =
            runTest {
                val direct = directReimbursement("immutable")
                repository.persist(direct)
                val conflictingDirect =
                    Reimbursement.recordDirect(
                        id = direct.id,
                        group = direct.group,
                        paidBy = direct.paidBy,
                        receivedBy = direct.receivedBy,
                        amount = MoneyAmount.ofCents(direct.amount.inCents() + 1),
                        reimbursedAt = direct.reimbursedAt,
                        declaredBy = direct.declaredBy,
                        declaredAt = direct.declaredAt,
                    )
                val pending = documentedReimbursement("terminal-conflict")
                val accepted = pending.accept(receiver("terminal-conflict"), DECIDED_AT)
                val rejected = pending.reject(receiver("terminal-conflict"), DECIDED_AT.plusSeconds(1))
                repository.persist(accepted)

                val errors =
                    listOf(
                        assertThrows<IllegalStateException> { repository.persist(conflictingDirect) },
                        assertThrows<IllegalStateException> { repository.persist(rejected) },
                    )

                assertEquals(
                    List(2) { "reimbursement persistence conflicts with its immutable history" },
                    errors.map { it.message },
                )
                assertEquals(
                    listOf(direct.toSnapshot(), accepted.toSnapshot()),
                    listOf(
                        repository.findByIdAndGroup(direct.id, direct.group)?.toSnapshot(),
                        repository.findByIdAndGroup(accepted.id, accepted.group)?.toSnapshot(),
                    ),
                )
            }
    }

    @Nested
    inner class FindByIdAndGroup {
        @Test
        fun `should return null for a missing reimbursement or another group`() =
            runTest {
                val reimbursement = directReimbursement("find-scope")
                val otherGroup = seedGroup("find-other")
                repository.persist(reimbursement)

                assertEquals(
                    listOf(null, null),
                    listOf(
                        repository.findByIdAndGroup(reimbursementId("missing"), reimbursement.group),
                        repository.findByIdAndGroup(reimbursement.id, otherGroup),
                    ),
                )
            }
    }

    @Nested
    inner class FindByGroup {
        @Test
        fun `should return an empty list for a group without reimbursements`() =
            runTest {
                val group = seedGroup("list-empty")

                assertEquals(emptyList<ReimbursementSnapshot>(), repository.findByGroup(group).map { it.toSnapshot() })
            }

        @Test
        fun `should return only the group reimbursements with complete state in deterministic order`() =
            runTest {
                val memberSeed = "list"
                val group = seedGroup(memberSeed)
                val direct =
                    directReimbursement(
                        seed = "list-direct",
                        group = group,
                        memberSeed = memberSeed,
                        reimbursedAt = REIMBURSED_AT.minusSeconds(2),
                    )
                val pending =
                    documentedReimbursement(
                        seed = "list-pending",
                        group = group,
                        memberSeed = memberSeed,
                        reimbursedAt = REIMBURSED_AT.minusSeconds(1),
                        documentCount = 2,
                    )
                val accepted =
                    documentedReimbursement(
                        seed = "list-accepted",
                        group = group,
                        memberSeed = memberSeed,
                        id = ReimbursementId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
                    ).accept(receiver(memberSeed), DECIDED_AT)
                val rejected =
                    documentedReimbursement(
                        seed = "list-rejected",
                        group = group,
                        memberSeed = memberSeed,
                        id = ReimbursementId(UUID.fromString("00000000-0000-0000-0000-000000000002")),
                    ).reject(
                        reviewedBy = receiver(memberSeed),
                        rejectedAt = DECIDED_AT,
                        reason = ReimbursementRejectionReason.of("Le justificatif ne correspond pas"),
                    )
                val otherGroupReimbursement = directReimbursement("list-other")
                listOf(direct, pending, accepted, rejected, otherGroupReimbursement).forEach { repository.persist(it) }

                assertEquals(
                    listOf(rejected, accepted, pending, direct).map { reimbursement -> reimbursement.toSnapshot() },
                    repository.findByGroup(group).map { reimbursement -> reimbursement.toSnapshot() },
                )
            }
    }

    private suspend fun directReimbursement(seed: String): Reimbursement =
        directReimbursement(
            seed = seed,
            group = seedGroup(seed),
            memberSeed = seed,
        )

    private fun directReimbursement(
        seed: String,
        group: GroupId,
        memberSeed: String,
        reimbursedAt: Instant = REIMBURSED_AT,
        id: ReimbursementId = reimbursementId(seed),
    ): Reimbursement =
        Reimbursement.recordDirect(
            id = id,
            group = group,
            paidBy = payer(memberSeed),
            receivedBy = receiver(memberSeed),
            amount = MoneyAmount.ofCents(4_200),
            reimbursedAt = reimbursedAt,
            declaredBy = receiver(memberSeed),
            declaredAt = DECLARED_AT,
        )

    private suspend fun documentedReimbursement(
        seed: String,
        documentCount: Int = 1,
    ): Reimbursement =
        documentedReimbursement(
            seed = seed,
            group = seedGroup(seed),
            memberSeed = seed,
            documentCount = documentCount,
        )

    private suspend fun documentedReimbursement(
        seed: String,
        group: GroupId,
        memberSeed: String,
        reimbursedAt: Instant = REIMBURSED_AT,
        documentCount: Int = 1,
        id: ReimbursementId = reimbursementId(seed),
    ): Reimbursement {
        val intents =
            (1..documentCount).map { index ->
                consumedIntent(seed, group, memberSeed, index).also { documentUploadIntentRepository.persist(it) }
            }
        return Reimbursement.declareWithSupportingDocuments(
            id = id,
            group = group,
            paidBy = payer(memberSeed),
            receivedBy = receiver(memberSeed),
            amount = MoneyAmount.ofCents(4_200),
            reimbursedAt = reimbursedAt,
            declaredBy = payer(memberSeed),
            declaredAt = DECLARED_AT,
            supportingDocumentUploadIntents = intents,
        )
    }

    private suspend fun seedGroup(seed: String): GroupId {
        val payer = payer(seed)
        val receiver = receiver(seed)
        val group = groupId("rr:$seed")
        memberRepository.persist(Member(payer, CREATED_AT))
        memberRepository.persist(Member(receiver, CREATED_AT))
        groupRepository.persist(
            Group
                .create(group, payer, CREATED_AT)
                .addMember(receiver, CREATED_AT),
        )
        return group
    }

    private fun consumedIntent(
        seed: String,
        group: GroupId,
        memberSeed: String,
        index: Int,
    ): DocumentUploadIntent =
        DocumentUploadIntent
            .create(
                id = DocumentUploadIntentId(namedUuid("document:$seed:$index")),
                group = group,
                uploader = payer(memberSeed),
                storageKey = DocumentStorageKey.of("groups/$seed/documents/$index.pdf"),
                fileName = DocumentFileName.of("Justificatif $index.pdf"),
                expectedMetadata = METADATA,
                createdAt = CREATED_AT,
                expiresAt = EXPIRES_AT,
            ).markReady(METADATA, READY_AT)
            .consume(CONSUMED_AT.plusSeconds(index.toLong()))

    private fun Reimbursement.toSnapshot(): ReimbursementSnapshot =
        ReimbursementSnapshot(
            id = id,
            group = group,
            paidBy = paidBy,
            receivedBy = receivedBy,
            amount = amount,
            reimbursedAt = reimbursedAt,
            declaredBy = declaredBy,
            declaredAt = declaredAt,
            status = status,
            supportingDocuments = supportingDocuments,
        )

    private fun payer(seed: String) = memberEmail("reimbursement-repository-$seed-payer")

    private fun receiver(seed: String) = memberEmail("reimbursement-repository-$seed-receiver")

    private fun reimbursementId(seed: String) = ReimbursementId(namedUuid("reimbursement:$seed"))

    private fun namedUuid(seed: String): UUID = UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8))

    private data class ReimbursementSnapshot(
        val id: ReimbursementId,
        val group: GroupId,
        val paidBy: MemberEmail,
        val receivedBy: MemberEmail,
        val amount: MoneyAmount,
        val reimbursedAt: Instant,
        val declaredBy: MemberEmail,
        val declaredAt: Instant,
        val status: ReimbursementStatus,
        val supportingDocuments: List<ReimbursementSupportingDocument>,
    )

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-10-03T10:00:00Z")
        val READY_AT: Instant = Instant.parse("2026-10-03T10:01:00Z")
        val CONSUMED_AT: Instant = Instant.parse("2026-10-03T10:02:00Z")
        val REIMBURSED_AT: Instant = Instant.parse("2026-10-03T10:03:00Z")
        val DECLARED_AT: Instant = Instant.parse("2026-10-03T10:04:00Z")
        val DECIDED_AT: Instant = Instant.parse("2026-10-03T10:05:00Z")
        val EXPIRES_AT: Instant = Instant.parse("2026-10-03T10:10:00Z")
        val METADATA =
            DocumentMetadata(
                mediaType = DocumentMediaType.of("application/pdf"),
                size = DocumentSize.ofBytes(512),
                checksum = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32))),
            )
    }
}
