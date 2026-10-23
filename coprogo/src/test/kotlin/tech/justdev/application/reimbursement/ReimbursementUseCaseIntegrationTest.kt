package tech.justdev.application.reimbursement

import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
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
import tech.justdev.domain.ledger.event.AcceptedReimbursementLedgerEvent
import tech.justdev.domain.ledger.repository.LedgerEventRepository
import tech.justdev.domain.reimbursement.entity.Reimbursement
import tech.justdev.domain.reimbursement.repository.ReimbursementRepository
import tech.justdev.domain.reimbursement.valueobject.ReimbursementRejectionReason
import tech.justdev.domain.reimbursement.valueobject.ReimbursementStatus
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.testsupport.PostgresMicronautTest
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.testUuid
import java.time.Instant
import java.util.Base64
import java.util.UUID

@PostgresMicronautTest
class ReimbursementUseCaseIntegrationTest {
    @Inject
    lateinit var recordDirectReimbursement: RecordDirectReimbursementUseCase

    @Inject
    lateinit var declareDocumentedReimbursement: DeclareDocumentedReimbursementUseCase

    @Inject
    lateinit var acceptReimbursement: AcceptReimbursementUseCase

    @Inject
    lateinit var rejectReimbursement: RejectReimbursementUseCase

    @Inject
    lateinit var reimbursementRepository: ReimbursementRepository

    @Inject
    lateinit var ledgerEventRepository: LedgerEventRepository

    @Inject
    lateinit var documentUploadIntentRepository: DocumentUploadIntentRepository

    @Inject
    lateinit var groupRepository: GroupRepository

    @Inject
    lateinit var memberRepository: MemberRepository

    @Nested
    inner class RecordDirectReimbursement {
        @Test
        fun `records an accepted reimbursement and its balance transfer atomically`() =
            runTest {
                val fixture = persistFixture("direct")

                recordDirectReimbursement(
                    RecordDirectReimbursementCommand(
                        group = fixture.group,
                        paidBy = fixture.payer,
                        recordedBy = fixture.receiver,
                        amountInCents = 4_200,
                        reimbursedAt = REIMBURSED_AT,
                        recordedAt = DECLARED_AT,
                    ),
                )

                val reimbursement = reimbursementRepository.findByGroup(fixture.group).single()
                assertEquals(
                    ReimbursementSnapshot(
                        paidBy = fixture.payer,
                        receivedBy = fixture.receiver,
                        declaredBy = fixture.receiver,
                        status = ReimbursementStatus.Accepted(DECLARED_AT),
                        supportingDocumentCount = 0,
                    ),
                    reimbursement.toSnapshot(),
                )
                assertAcceptedLedgerEvent(reimbursement)
            }
    }

    @Nested
    inner class AcceptDocumentedReimbursement {
        @Test
        fun `consumes the document then records the receiver acceptance and balance transfer`() =
            runTest {
                val fixture = persistFixture("accepted-documented")
                val uploadIntent = persistReadyUploadIntent(fixture)
                declareDocumented(fixture, uploadIntent.id)
                val pending = reimbursementRepository.findByGroup(fixture.group).single()

                assertEquals(ReimbursementStatus.PendingReview, pending.status)
                assertEquals(emptyList<Any>(), ledgerEventRepository.findByGroup(fixture.group))
                assertEquals(
                    DocumentUploadIntentStatus.Consumed(READY_AT, DECLARED_AT),
                    documentUploadIntentRepository.findByIdAndGroup(uploadIntent.id, fixture.group)?.status,
                )

                acceptReimbursement(
                    AcceptReimbursementCommand(
                        group = fixture.group,
                        reimbursement = pending.id,
                        acceptedBy = fixture.receiver,
                        acceptedAt = DECIDED_AT,
                    ),
                )

                val accepted = requireNotNull(reimbursementRepository.findByIdAndGroup(pending.id, fixture.group))
                assertEquals(ReimbursementStatus.Accepted(DECIDED_AT), accepted.status)
                assertAcceptedLedgerEvent(accepted)
            }
    }

    @Nested
    inner class RejectDocumentedReimbursement {
        @Test
        fun `records the receiver rejection and appends no balance transfer`() =
            runTest {
                val fixture = persistFixture("rejected-documented")
                val uploadIntent = persistReadyUploadIntent(fixture)
                val reason = ReimbursementRejectionReason.of("The amount does not match the receipt")
                declareDocumented(fixture, uploadIntent.id)
                val pending = reimbursementRepository.findByGroup(fixture.group).single()

                rejectReimbursement(
                    RejectReimbursementCommand(
                        group = fixture.group,
                        reimbursement = pending.id,
                        rejectedBy = fixture.receiver,
                        rejectedAt = DECIDED_AT,
                        reason = reason,
                    ),
                )

                val rejected = requireNotNull(reimbursementRepository.findByIdAndGroup(pending.id, fixture.group))
                assertEquals(ReimbursementStatus.Rejected(DECIDED_AT, reason), rejected.status)
                assertEquals(emptyList<Any>(), ledgerEventRepository.findByGroup(fixture.group))
            }
    }

    private suspend fun declareDocumented(
        fixture: Fixture,
        uploadIntent: DocumentUploadIntentId,
    ) {
        declareDocumentedReimbursement(
            DeclareDocumentedReimbursementCommand(
                group = fixture.group,
                paidBy = fixture.payer,
                receivedBy = fixture.receiver,
                amountInCents = 4_200,
                reimbursedAt = REIMBURSED_AT,
                declaredAt = DECLARED_AT,
                supportingDocumentUploadIntents = setOf(uploadIntent),
            ),
        )
    }

    private suspend fun persistFixture(seed: String): Fixture {
        val uniqueSeed = "${UUID.randomUUID()}-$seed"
        val payer = memberEmail("$uniqueSeed-payer")
        val receiver = memberEmail("$uniqueSeed-receiver")
        val group = groupId("$uniqueSeed-group")
        memberRepository.persist(Member(payer, CREATED_AT.minusSeconds(120)))
        memberRepository.persist(Member(receiver, CREATED_AT.minusSeconds(90)))
        groupRepository.persist(
            Group
                .create(group, receiver, CREATED_AT.minusSeconds(60))
                .addMember(payer, CREATED_AT.minusSeconds(30)),
        )
        return Fixture(uniqueSeed, group, payer, receiver)
    }

    private suspend fun persistReadyUploadIntent(fixture: Fixture): DocumentUploadIntent =
        DocumentUploadIntent
            .create(
                id = DocumentUploadIntentId(testUuid("${fixture.seed}-document")),
                group = fixture.group,
                uploader = fixture.payer,
                storageKey =
                    DocumentStorageKey.of(
                        "groups/${fixture.group.toPrimitive()}/documents/${fixture.seed}.pdf",
                    ),
                fileName = DocumentFileName.of("receipt.pdf"),
                expectedMetadata = METADATA,
                createdAt = CREATED_AT,
                expiresAt = EXPIRES_AT,
            ).markReady(METADATA, READY_AT)
            .also { intent -> documentUploadIntentRepository.persist(intent) }

    private suspend fun assertAcceptedLedgerEvent(reimbursement: Reimbursement) {
        val events = ledgerEventRepository.findByGroup(reimbursement.group)
        assertEquals(1, events.size)
        assertEquals(reimbursement.id, (events.single() as AcceptedReimbursementLedgerEvent).reimbursement)
    }

    private fun Reimbursement.toSnapshot() =
        ReimbursementSnapshot(
            paidBy = paidBy,
            receivedBy = receivedBy,
            declaredBy = declaredBy,
            status = status,
            supportingDocumentCount = supportingDocuments.size,
        )

    private data class Fixture(
        val seed: String,
        val group: GroupId,
        val payer: MemberEmail,
        val receiver: MemberEmail,
    )

    private data class ReimbursementSnapshot(
        val paidBy: MemberEmail,
        val receivedBy: MemberEmail,
        val declaredBy: MemberEmail,
        val status: ReimbursementStatus,
        val supportingDocumentCount: Int,
    )

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-04-03T08:00:00Z")
        val READY_AT: Instant = Instant.parse("2026-04-03T09:00:00Z")
        val REIMBURSED_AT: Instant = Instant.parse("2026-04-03T09:30:00Z")
        val DECLARED_AT: Instant = Instant.parse("2026-04-03T10:00:00Z")
        val DECIDED_AT: Instant = Instant.parse("2026-04-03T10:30:00Z")
        val EXPIRES_AT: Instant = Instant.parse("2026-04-03T12:00:00Z")
        val METADATA =
            DocumentMetadata(
                mediaType = DocumentMediaType.of("application/pdf"),
                size = DocumentSize.ofBytes(512),
                checksum = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32))),
            )
    }
}
