package tech.justdev.infrastructure.persistence.reimbursement

import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.application.reimbursement.DocumentedReimbursementDeclaration
import tech.justdev.application.shared.TransactionRunner
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
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.reimbursement.entity.Reimbursement
import tech.justdev.domain.reimbursement.repository.ReimbursementRepository
import tech.justdev.domain.reimbursement.valueobject.ReimbursementId
import tech.justdev.domain.reimbursement.valueobject.ReimbursementStatus
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.testsupport.PostgresMicronautTest
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.testUuid
import java.time.Instant
import java.util.Base64
import java.util.UUID

@PostgresMicronautTest
class R2dbcReimbursementDeclarationPersistenceIntegrationTest {
    @Inject
    lateinit var persistence: R2dbcReimbursementDeclarationPersistence

    @Inject
    lateinit var transactionRunner: TransactionRunner

    @Inject
    lateinit var documentUploadIntentRepository: DocumentUploadIntentRepository

    @Inject
    lateinit var reimbursementRepository: ReimbursementRepository

    @Inject
    lateinit var memberRepository: MemberRepository

    @Inject
    lateinit var groupRepository: GroupRepository

    @Nested
    inner class Persist {
        @Test
        fun `should persist consumed documents and their reimbursement atomically`() =
            runTest {
                val fixture = persistFixture("success")
                val first = readyIntent("first", fixture)
                val second = readyIntent("second", fixture)
                documentUploadIntentRepository.persistAll(listOf(first, second))
                val consumed = listOf(first.consume(DECLARED_AT), second.consume(DECLARED_AT))
                val reimbursement = documentedReimbursement(fixture, consumed)

                persistence.persist(DocumentedReimbursementDeclaration.from(reimbursement, consumed))

                assertEquals(
                    listOf(reimbursement.toSnapshot()),
                    listOf(requireNotNull(reimbursementRepository.findByIdAndGroup(reimbursement.id, fixture.group)).toSnapshot()),
                )
                assertEquals(
                    consumed.map { intent -> intent.status },
                    listOf(first, second).map { intent ->
                        requireNotNull(documentUploadIntentRepository.findByIdAndGroup(intent.id, fixture.group)).status
                    },
                )
            }

        @Test
        fun `should roll back consumed documents when reimbursement persistence fails`() =
            runTest {
                val fixture = persistFixture("rollback")
                val intent = readyIntent("document", fixture)
                documentUploadIntentRepository.persist(intent)
                val consumed = intent.consume(DECLARED_AT)
                val reimbursement = documentedReimbursement(fixture, listOf(consumed))
                val failingPersistence =
                    R2dbcReimbursementDeclarationPersistence(
                        transactionRunner = transactionRunner,
                        documentUploadIntentRepository = documentUploadIntentRepository,
                        reimbursementRepository = FailingReimbursementRepository,
                    )

                val error =
                    assertThrows<IllegalStateException> {
                        failingPersistence.persist(DocumentedReimbursementDeclaration.from(reimbursement, listOf(consumed)))
                    }

                assertEquals("reimbursement persistence failed", error.message)
                assertNull(reimbursementRepository.findByIdAndGroup(reimbursement.id, fixture.group))
                assertEquals(
                    DocumentUploadIntentStatus.Ready(READY_AT),
                    requireNotNull(documentUploadIntentRepository.findByIdAndGroup(intent.id, fixture.group)).status,
                )
            }
    }

    private suspend fun persistFixture(seed: String): Fixture {
        val uniqueSeed = "${UUID.randomUUID()}-$seed"
        val payer = memberEmail("$uniqueSeed-bob")
        val receiver = memberEmail("$uniqueSeed-alice")
        val group = groupId("$uniqueSeed-group")
        memberRepository.persist(Member(payer, CREATED_AT.minusSeconds(120)))
        memberRepository.persist(Member(receiver, CREATED_AT.minusSeconds(120)))
        groupRepository.persist(
            Group
                .create(group, receiver, CREATED_AT.minusSeconds(90))
                .addMember(payer, CREATED_AT.minusSeconds(60)),
        )
        return Fixture(uniqueSeed, group, payer, receiver)
    }

    private fun readyIntent(
        documentSeed: String,
        fixture: Fixture,
    ): DocumentUploadIntent {
        val seed = "${fixture.seed}-$documentSeed"
        return DocumentUploadIntent
            .create(
                id = DocumentUploadIntentId(testUuid("$documentSeed:$seed")),
                group = fixture.group,
                uploader = fixture.payer,
                storageKey = DocumentStorageKey.of("groups/${fixture.group.toPrimitive()}/documents/$seed.pdf"),
                fileName = DocumentFileName.of("$seed.pdf"),
                expectedMetadata = METADATA,
                createdAt = CREATED_AT,
                expiresAt = EXPIRES_AT,
            ).markReady(METADATA, READY_AT)
    }

    private fun documentedReimbursement(
        fixture: Fixture,
        consumedUploadIntents: List<DocumentUploadIntent>,
    ): Reimbursement =
        Reimbursement.declareWithSupportingDocuments(
            id = ReimbursementId(testUuid("reimbursement:${fixture.seed}")),
            group = fixture.group,
            paidBy = fixture.payer,
            receivedBy = fixture.receiver,
            amount = MoneyAmount.ofCents(4_200),
            reimbursedAt = REIMBURSED_AT,
            declaredBy = fixture.payer,
            declaredAt = DECLARED_AT,
            supportingDocumentUploadIntents = consumedUploadIntents,
        )

    private fun Reimbursement.toSnapshot() =
        ReimbursementSnapshot(
            id = id.toPrimitive(),
            group = group.toPrimitive(),
            paidBy = paidBy.toPrimitive(),
            receivedBy = receivedBy.toPrimitive(),
            status = status,
            supportingDocumentUploadIntents = supportingDocuments.map { document -> document.sourceUploadIntent.toPrimitive() },
        )

    private object FailingReimbursementRepository : ReimbursementRepository {
        override suspend fun findByIdAndGroup(
            id: ReimbursementId,
            group: GroupId,
        ): Reimbursement? = error("not used")

        override suspend fun findByGroup(group: GroupId): List<Reimbursement> = error("not used")

        override suspend fun persist(reimbursement: Reimbursement): Nothing = error("reimbursement persistence failed")
    }

    private data class Fixture(
        val seed: String,
        val group: GroupId,
        val payer: MemberEmail,
        val receiver: MemberEmail,
    )

    private data class ReimbursementSnapshot(
        val id: UUID,
        val group: UUID,
        val paidBy: String,
        val receivedBy: String,
        val status: ReimbursementStatus,
        val supportingDocumentUploadIntents: List<UUID>,
    )

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-04-03T08:00:00Z")
        val READY_AT: Instant = Instant.parse("2026-04-03T09:00:00Z")
        val REIMBURSED_AT: Instant = Instant.parse("2026-04-03T09:30:00Z")
        val DECLARED_AT: Instant = Instant.parse("2026-04-03T10:00:00Z")
        val EXPIRES_AT: Instant = Instant.parse("2026-04-03T11:00:00Z")
        val METADATA =
            DocumentMetadata(
                mediaType = DocumentMediaType.of("application/pdf"),
                size = DocumentSize.ofBytes(512),
                checksum = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32))),
            )
    }
}
