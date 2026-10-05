package tech.justdev.infrastructure.persistence.reimbursement

import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.application.reimbursement.AcceptedReimbursementPersistence
import tech.justdev.application.reimbursement.DocumentedReimbursementDeclaration
import tech.justdev.application.reimbursement.ReimbursementDeclarationPersistence
import tech.justdev.application.shared.TransactionRunner
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
import tech.justdev.domain.ledger.event.AcceptedReimbursementLedgerEvent
import tech.justdev.domain.ledger.event.LedgerEvent
import tech.justdev.domain.ledger.repository.LedgerEventRepository
import tech.justdev.domain.reimbursement.entity.Reimbursement
import tech.justdev.domain.reimbursement.entity.ReimbursementSupportingDocument
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
class R2dbcAcceptedReimbursementPersistenceIntegrationTest {
    @Inject
    lateinit var reimbursementRepository: ReimbursementRepository

    @Inject
    lateinit var acceptedReimbursementPersistence: AcceptedReimbursementPersistence

    @Inject
    lateinit var ledgerEventRepository: LedgerEventRepository

    @Inject
    lateinit var reimbursementDeclarationPersistence: ReimbursementDeclarationPersistence

    @Inject
    lateinit var documentUploadIntentRepository: DocumentUploadIntentRepository

    @Inject
    lateinit var groupRepository: GroupRepository

    @Inject
    lateinit var memberRepository: MemberRepository

    @Inject
    lateinit var transactionRunner: TransactionRunner

    @Test
    fun `persist should atomically store the accepted reimbursement and its ledger event`() =
        runTest {
            val fixture = persistFixture("success")
            val accepted = directReimbursement(fixture)

            acceptedReimbursementPersistence.persist(accepted)

            assertEquals(
                accepted.toSnapshot(),
                requireNotNull(reimbursementRepository.findByIdAndGroup(accepted.id, accepted.group)).toSnapshot(),
            )
            assertEquals(
                listOf(AcceptedReimbursementLedgerEvent.from(accepted)),
                ledgerEventRepository.findByGroup(accepted.group),
            )
        }

    @Test
    fun `persist should roll back a new reimbursement when ledger append fails`() {
        var fixture: Fixture? = null
        val error =
            assertThrows<IllegalStateException> {
                runTest {
                    fixture = persistFixture("direct-rollback")
                    R2dbcAcceptedReimbursementPersistence(
                        transactionRunner = transactionRunner,
                        reimbursementRepository = reimbursementRepository,
                        ledgerEventRepository = FailingLedgerEventRepository,
                    ).persist(directReimbursement(requireNotNull(fixture)))
                }
            }

        assertEquals("ledger append failed", error.message)
        val persistedFixture = requireNotNull(fixture)
        assertNull(
            runBlocking {
                reimbursementRepository.findByIdAndGroup(persistedFixture.reimbursement, persistedFixture.group)
            },
        )
    }

    @Test
    fun `persist should roll back an acceptance decision when ledger append fails`() {
        var fixture: Fixture? = null
        val error =
            assertThrows<IllegalStateException> {
                runTest {
                    fixture = persistFixture("accept-rollback")
                    val pending = persistPendingReimbursement(requireNotNull(fixture))
                    R2dbcAcceptedReimbursementPersistence(
                        transactionRunner = transactionRunner,
                        reimbursementRepository = reimbursementRepository,
                        ledgerEventRepository = FailingLedgerEventRepository,
                    ).persist(pending.accept(pending.receivedBy, ACCEPTED_AT))
                }
            }

        assertEquals("ledger append failed", error.message)
        val persistedFixture = requireNotNull(fixture)
        val stored =
            runBlocking {
                requireNotNull(
                    reimbursementRepository.findByIdAndGroup(
                        persistedFixture.reimbursement,
                        persistedFixture.group,
                    ),
                )
            }
        assertEquals(ReimbursementStatus.PendingReview, stored.status)
    }

    private fun directReimbursement(fixture: Fixture): Reimbursement =
        Reimbursement.recordDirect(
            id = fixture.reimbursement,
            group = fixture.group,
            paidBy = fixture.payer,
            receivedBy = fixture.receiver,
            amount = MoneyAmount.ofCents(4_200),
            reimbursedAt = REIMBURSED_AT,
            declaredBy = fixture.receiver,
            declaredAt = DECLARED_AT,
        )

    private suspend fun persistFixture(seed: String): Fixture {
        val uniqueSeed = "${UUID.randomUUID()}-$seed"
        val payer = memberEmail("$uniqueSeed-payer")
        val receiver = memberEmail("$uniqueSeed-receiver")
        val group = groupId("$uniqueSeed-group")
        val reimbursement = ReimbursementId(testUuid("$uniqueSeed-reimbursement"))
        memberRepository.persist(Member(payer, CREATED_AT.minusSeconds(60)))
        memberRepository.persist(Member(receiver, CREATED_AT.minusSeconds(30)))
        groupRepository.persist(
            Group
                .create(group, receiver, CREATED_AT)
                .addMember(payer, CREATED_AT.plusSeconds(1)),
        )
        return Fixture(uniqueSeed, group, payer, receiver, reimbursement)
    }

    private suspend fun persistPendingReimbursement(fixture: Fixture): Reimbursement {
        val readyIntent =
            DocumentUploadIntent
                .create(
                    id = DocumentUploadIntentId(testUuid("${fixture.seed}-document")),
                    group = fixture.group,
                    uploader = fixture.payer,
                    storageKey = DocumentStorageKey.of("groups/${fixture.group.toPrimitive()}/documents/reimbursement.pdf"),
                    fileName = DocumentFileName.of("reimbursement.pdf"),
                    expectedMetadata = METADATA,
                    createdAt = CREATED_AT,
                    expiresAt = EXPIRES_AT,
                ).markReady(METADATA, READY_AT)
        documentUploadIntentRepository.persist(readyIntent)
        val consumedIntent = readyIntent.consume(DECLARED_AT)
        val pending =
            Reimbursement.declareWithSupportingDocuments(
                id = fixture.reimbursement,
                group = fixture.group,
                paidBy = fixture.payer,
                receivedBy = fixture.receiver,
                amount = MoneyAmount.ofCents(4_200),
                reimbursedAt = REIMBURSED_AT,
                declaredBy = fixture.payer,
                declaredAt = DECLARED_AT,
                supportingDocumentUploadIntents = listOf(consumedIntent),
            )
        reimbursementDeclarationPersistence.persist(
            DocumentedReimbursementDeclaration.from(pending, listOf(consumedIntent)),
        )
        return pending
    }

    private data class Fixture(
        val seed: String,
        val group: GroupId,
        val payer: MemberEmail,
        val receiver: MemberEmail,
        val reimbursement: ReimbursementId,
    )

    private fun Reimbursement.toSnapshot() =
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
        val CREATED_AT: Instant = Instant.parse("2026-04-03T08:00:00Z")
        val READY_AT: Instant = Instant.parse("2026-04-03T09:00:00Z")
        val REIMBURSED_AT: Instant = Instant.parse("2026-04-03T09:30:00Z")
        val DECLARED_AT: Instant = Instant.parse("2026-04-03T10:00:00Z")
        val ACCEPTED_AT: Instant = Instant.parse("2026-04-03T11:00:00Z")
        val EXPIRES_AT: Instant = Instant.parse("2026-04-03T12:00:00Z")
        val METADATA =
            DocumentMetadata(
                mediaType = DocumentMediaType.of("application/pdf"),
                size = DocumentSize.ofBytes(512),
                checksum = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32))),
            )
    }
}

private object FailingLedgerEventRepository : LedgerEventRepository {
    override suspend fun append(event: LedgerEvent): Nothing = throw IllegalStateException("ledger append failed")

    override suspend fun findByGroup(group: GroupId): List<LedgerEvent> = throw AssertionError("ledger events should not be read")
}
