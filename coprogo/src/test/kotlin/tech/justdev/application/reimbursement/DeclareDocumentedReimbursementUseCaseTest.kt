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
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.reimbursement.entity.Reimbursement
import tech.justdev.domain.reimbursement.valueobject.ReimbursementId
import tech.justdev.domain.reimbursement.valueobject.ReimbursementStatus
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.testUuid
import java.time.Instant
import java.util.Base64
import java.util.UUID

class DeclareDocumentedReimbursementUseCaseTest {
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class Invoke {
        private fun unavailableIntentCases(): List<UnavailableIntentCase> {
            val missing = readyIntent("missing")
            val crossGroup = readyIntent("cross-group", group = groupId("another-group"))
            val wrongUploader = readyIntent("wrong-uploader", uploader = memberEmail("alice"))
            val pending = pendingIntent("pending")
            val consumed = readyIntent("consumed").consume(DECLARED_AT.minusSeconds(1))
            val expired = readyIntent("expired", expiresAt = DECLARED_AT.minusSeconds(1))
            return listOf(
                UnavailableIntentCase(emptyList(), setOf(missing.id)),
                UnavailableIntentCase(listOf(crossGroup), setOf(crossGroup.id)),
                UnavailableIntentCase(listOf(wrongUploader), setOf(wrongUploader.id)),
                UnavailableIntentCase(listOf(pending), setOf(pending.id)),
                UnavailableIntentCase(listOf(consumed), setOf(consumed.id)),
                UnavailableIntentCase(listOf(expired), setOf(expired.id)),
            )
        }

        @Test
        fun `should persist a pending reimbursement and its consumed upload intents`() =
            runTest {
                val first = readyIntent("first")
                val second = readyIntent("second")
                val persistence = RecordingReimbursementDeclarationPersistence()
                val useCase =
                    useCase(
                        documentUploadIntentRepository = RecordingDocumentUploadIntentRepository(listOf(second, first)),
                        persistence = persistence,
                    )

                useCase(command(setOf(second.id, first.id)))

                assertEquals(
                    listOf(
                        ReimbursementSnapshot(
                            id = testUuid("reimbursement:documented"),
                            group = GROUP.toPrimitive(),
                            paidBy = "bob@example.com",
                            receivedBy = "alice@example.com",
                            amountInCents = 4_200,
                            reimbursedAt = REIMBURSED_AT,
                            declaredBy = "bob@example.com",
                            declaredAt = DECLARED_AT,
                            status = ReimbursementStatus.PendingReview,
                            supportingDocumentUploadIntents = listOf(first.id.toPrimitive(), second.id.toPrimitive()),
                        ),
                    ),
                    persistence.persisted.map { declaration -> declaration.reimbursement.toSnapshot() },
                )
                assertEquals(
                    listOf(
                        IntentSnapshot(first.id.toPrimitive(), DocumentUploadIntentStatus.Consumed(READY_AT, DECLARED_AT)),
                        IntentSnapshot(second.id.toPrimitive(), DocumentUploadIntentStatus.Consumed(READY_AT, DECLARED_AT)),
                    ),
                    persistence.persisted
                        .single()
                        .consumedUploadIntents
                        .map { intent -> IntentSnapshot(intent.id.toPrimitive(), intent.status) },
                )
            }

        @ParameterizedTest(name = "{index}")
        @MethodSource("unavailableIntentCases")
        fun `should reject unavailable upload intents before generating an id`(case: UnavailableIntentCase) {
            val persistence = RecordingReimbursementDeclarationPersistence()

            val error =
                assertThrows<SupportingDocumentUploadIntentUnavailableException> {
                    runTest {
                        useCase(
                            documentUploadIntentRepository = RecordingDocumentUploadIntentRepository(case.stored),
                            persistence = persistence,
                            idGenerator = failingIdGenerator(),
                        )(command(case.requested))
                    }
                }

            assertEquals("supporting document upload intent is unavailable", error.message)
            assertEquals(emptyList<DocumentedReimbursementDeclaration>(), persistence.persisted)
        }

        @Test
        fun `should reject an empty upload intent set before reading documents or generating an id`() {
            val persistence = RecordingReimbursementDeclarationPersistence()

            val error =
                assertThrows<SupportingDocumentUploadIntentUnavailableException> {
                    runTest {
                        useCase(
                            documentUploadIntentRepository = FailingDocumentUploadIntentRepository,
                            persistence = persistence,
                            idGenerator = failingIdGenerator(),
                        )(command(emptySet()))
                    }
                }

            assertEquals("supporting document upload intent is unavailable", error.message)
            assertEquals(emptyList<DocumentedReimbursementDeclaration>(), persistence.persisted)
        }

        @Test
        fun `should verify payer membership before reading documents or generating an id`() {
            assertThrows<GroupAccessDeniedException> {
                runTest {
                    useCase(
                        documentUploadIntentRepository = FailingDocumentUploadIntentRepository,
                        persistence = RecordingReimbursementDeclarationPersistence(),
                        idGenerator = failingIdGenerator(),
                    )(command(setOf(DocumentUploadIntentId(testUuid("membership"))), paidBy = memberEmail("outsider")))
                }
            }
        }

        @Test
        fun `should verify receiver membership before reading documents or generating an id`() {
            val error =
                assertThrows<IllegalArgumentException> {
                    runTest {
                        useCase(
                            documentUploadIntentRepository = FailingDocumentUploadIntentRepository,
                            persistence = RecordingReimbursementDeclarationPersistence(),
                            idGenerator = failingIdGenerator(),
                        )(command(setOf(DocumentUploadIntentId(testUuid("membership"))), receivedBy = memberEmail("outsider")))
                    }
                }

            assertEquals(
                "reimbursement receiver outsider@example.com is not part of group ${GROUP.toPrimitive()}",
                error.message,
            )
        }
    }

    private fun useCase(
        documentUploadIntentRepository: DocumentUploadIntentRepository,
        persistence: ReimbursementDeclarationPersistence,
        idGenerator: ReimbursementIdGenerator =
            ReimbursementIdGenerator { ReimbursementId(testUuid("reimbursement:documented")) },
    ): DeclareDocumentedReimbursementUseCase =
        DeclareDocumentedReimbursementUseCaseImpl(
            groupAccessPolicy = GroupAccessPolicy(InMemoryGroupRepository(listOf(group()))),
            reimbursementIdGenerator = idGenerator,
            documentUploadIntentRepository = documentUploadIntentRepository,
            reimbursementDeclarationPersistence = persistence,
        )

    private fun failingIdGenerator() = ReimbursementIdGenerator { throw AssertionError("reimbursement id should not be generated") }

    private fun command(
        supportingDocumentUploadIntents: Set<DocumentUploadIntentId>,
        paidBy: MemberEmail = memberEmail("bob"),
        receivedBy: MemberEmail = memberEmail("alice"),
    ) = DeclareDocumentedReimbursementCommand(
        group = GROUP,
        paidBy = paidBy,
        receivedBy = receivedBy,
        amountInCents = 4_200,
        reimbursedAt = REIMBURSED_AT,
        declaredAt = DECLARED_AT,
        supportingDocumentUploadIntents = supportingDocumentUploadIntents,
    )

    private fun group(): Group =
        Group
            .create(GROUP, memberEmail("alice"), CREATED_AT)
            .addMember(memberEmail("bob"), CREATED_AT.plusSeconds(1))

    private fun pendingIntent(
        seed: String,
        group: GroupId = GROUP,
        uploader: MemberEmail = memberEmail("bob"),
        expiresAt: Instant = EXPIRES_AT,
    ): DocumentUploadIntent =
        DocumentUploadIntent.create(
            id = DocumentUploadIntentId(testUuid("document:$seed")),
            group = group,
            uploader = uploader,
            storageKey = DocumentStorageKey.of("groups/${group.toPrimitive()}/documents/$seed.pdf"),
            fileName = DocumentFileName.of("$seed.pdf"),
            expectedMetadata = METADATA,
            createdAt = CREATED_AT,
            expiresAt = expiresAt,
        )

    private fun readyIntent(
        seed: String,
        group: GroupId = GROUP,
        uploader: MemberEmail = memberEmail("bob"),
        expiresAt: Instant = EXPIRES_AT,
    ): DocumentUploadIntent = pendingIntent(seed, group, uploader, expiresAt).markReady(METADATA, READY_AT)

    private fun Reimbursement.toSnapshot() =
        ReimbursementSnapshot(
            id = id.toPrimitive(),
            group = group.toPrimitive(),
            paidBy = paidBy.toPrimitive(),
            receivedBy = receivedBy.toPrimitive(),
            amountInCents = amount.inCents(),
            reimbursedAt = reimbursedAt,
            declaredBy = declaredBy.toPrimitive(),
            declaredAt = declaredAt,
            status = status,
            supportingDocumentUploadIntents = supportingDocuments.map { document -> document.sourceUploadIntent.toPrimitive() },
        )

    private class RecordingDocumentUploadIntentRepository(
        intents: Iterable<DocumentUploadIntent>,
    ) : DocumentUploadIntentRepository {
        private val intentsById = intents.associateBy(DocumentUploadIntent::id)

        override suspend fun findReadyByIdsAndGroupAndUploader(
            ids: Set<DocumentUploadIntentId>,
            group: GroupId,
            uploader: MemberEmail,
        ): List<DocumentUploadIntent> =
            intentsById.values
                .filter { intent ->
                    intent.id in ids &&
                        intent.group == group &&
                        intent.uploader == uploader &&
                        intent.status is DocumentUploadIntentStatus.Ready
                }.sortedBy { intent -> intent.id.toPrimitive() }

        override suspend fun persist(intent: DocumentUploadIntent) = error("not used")

        override suspend fun persistAll(intents: List<DocumentUploadIntent>) = error("not used")

        override suspend fun findPendingByIdAndGroupAndUploader(
            id: DocumentUploadIntentId,
            group: GroupId,
            uploader: MemberEmail,
        ): DocumentUploadIntent? = error("not used")

        override suspend fun findByIdAndGroup(
            id: DocumentUploadIntentId,
            group: GroupId,
        ): DocumentUploadIntent? = error("not used")
    }

    private object FailingDocumentUploadIntentRepository : DocumentUploadIntentRepository {
        override suspend fun findReadyByIdsAndGroupAndUploader(
            ids: Set<DocumentUploadIntentId>,
            group: GroupId,
            uploader: MemberEmail,
        ): List<DocumentUploadIntent> = error("documents must not be read")

        override suspend fun persist(intent: DocumentUploadIntent) = error("not used")

        override suspend fun persistAll(intents: List<DocumentUploadIntent>) = error("not used")

        override suspend fun findPendingByIdAndGroupAndUploader(
            id: DocumentUploadIntentId,
            group: GroupId,
            uploader: MemberEmail,
        ): DocumentUploadIntent? = error("not used")

        override suspend fun findByIdAndGroup(
            id: DocumentUploadIntentId,
            group: GroupId,
        ): DocumentUploadIntent? = error("not used")
    }

    private class RecordingReimbursementDeclarationPersistence : ReimbursementDeclarationPersistence {
        val persisted = mutableListOf<DocumentedReimbursementDeclaration>()

        override suspend fun persist(declaration: DocumentedReimbursementDeclaration) {
            persisted += declaration
        }
    }

    private data class IntentSnapshot(
        val id: UUID,
        val status: DocumentUploadIntentStatus,
    )

    private data class ReimbursementSnapshot(
        val id: UUID,
        val group: UUID,
        val paidBy: String,
        val receivedBy: String,
        val amountInCents: Long,
        val reimbursedAt: Instant,
        val declaredBy: String,
        val declaredAt: Instant,
        val status: ReimbursementStatus,
        val supportingDocumentUploadIntents: List<UUID>,
    )

    data class UnavailableIntentCase(
        val stored: List<DocumentUploadIntent>,
        val requested: Set<DocumentUploadIntentId>,
    )

    private companion object {
        val GROUP: GroupId = groupId("documented-reimbursement")
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
