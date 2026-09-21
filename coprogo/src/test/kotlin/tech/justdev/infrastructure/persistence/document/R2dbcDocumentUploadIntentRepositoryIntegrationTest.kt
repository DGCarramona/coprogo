package tech.justdev.infrastructure.persistence.document

import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.infrastructure.persistence.jooq.R2dbcTransactionRunner
import tech.justdev.testsupport.PostgresMicronautTest
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.testUuid
import java.time.Instant
import java.util.Base64

@PostgresMicronautTest
class R2dbcDocumentUploadIntentRepositoryIntegrationTest {
    @Inject
    lateinit var repository: DocumentUploadIntentRepository

    @Inject
    lateinit var memberRepository: MemberRepository

    @Inject
    lateinit var groupRepository: GroupRepository

    @Inject
    lateinit var transactionRunner: R2dbcTransactionRunner

    @Nested
    inner class Persist {
        @Test
        fun `should round trip pending ready and consumed upload intents`() =
            runTest {
                val expected =
                    listOf(
                        "pending" to { intent: DocumentUploadIntent -> intent },
                        "ready" to { intent: DocumentUploadIntent -> intent.markReady(METADATA, READY_AT) },
                        "consumed" to
                            { intent: DocumentUploadIntent ->
                                intent.markReady(METADATA, READY_AT).consume(CONSUMED_AT)
                            },
                    ).map { (seed, transition) ->
                        seedGroup(seed)
                        transition(pendingIntent(seed))
                    }
                for (intent in expected) {
                    repository.persist(intent)
                }

                assertIntentsEqual(
                    expected,
                    expected.map { repository.findByIdAndGroup(it.id, it.group) },
                )
            }

        @Test
        fun `should update transitions for the same upload intent`() =
            runTest {
                val seed = "transitions"
                seedGroup(seed)
                val pending = pendingIntent(seed)
                val ready = pending.markReady(METADATA, READY_AT)
                val consumed = ready.consume(CONSUMED_AT)

                val expected = listOf(pending, ready, consumed)
                val actual =
                    expected.map {
                        repository.persist(it)
                        repository.findByIdAndGroup(it.id, it.group)
                    }

                assertIntentsEqual(expected, actual)
            }

        @Test
        fun `should reject reusing an id for another group`() {
            val error =
                assertThrows<IllegalStateException> {
                    runTest {
                        seedGroup("collision-source")
                        seedGroup("collision-other")
                        val stored = pendingIntent("collision-source")
                        val collision = pendingIntent("collision-other", id = stored.id)
                        repository.persist(stored)

                        repository.persist(collision)
                    }
                }

            assertEquals("document upload intent persistence must affect exactly one row", error.message)
        }

        @Test
        fun `should reject stale lifecycle regressions`() {
            val seed = "stale-lifecycle"
            val pending = pendingIntent(seed)
            val ready = pending.markReady(METADATA, READY_AT)
            val consumed = ready.consume(CONSUMED_AT)
            runTest {
                seedGroup(seed)
                repository.persist(consumed)
            }

            assertEquals(
                List(2) { PERSISTENCE_REJECTION },
                listOf(pending, ready).map(::persistenceFailure),
            )

            runTest {
                assertIntentEquals(consumed, repository.findByIdAndGroup(consumed.id, consumed.group))
            }
        }

        @Test
        fun `should reject rewriting persisted transition timestamps`() {
            val seed = "transition-timestamps"
            val pending = pendingIntent(seed)
            val ready = pending.markReady(METADATA, READY_AT)
            runTest {
                seedGroup(seed)
                repository.persist(ready)
            }

            assertEquals(PERSISTENCE_REJECTION, persistenceFailure(pending.markReady(METADATA, READY_AT.plusSeconds(1))))
        }

        @Test
        fun `should allow only one consumed transition writer`() {
            val seed = "consumed-writer"
            val ready = pendingIntent(seed).markReady(METADATA, READY_AT)
            runTest {
                seedGroup(seed)
                repository.persist(ready)
            }

            val consumed = ready.consume(CONSUMED_AT)
            runTest { repository.persist(consumed) }
            assertEquals(
                listOf(PERSISTENCE_REJECTION, PERSISTENCE_REJECTION),
                listOf(consumed, ready.consume(CONSUMED_AT.plusSeconds(1))).map(::persistenceFailure),
            )

            runTest {
                assertIntentEquals(consumed, repository.findByIdAndGroup(consumed.id, consumed.group))
            }
        }

        @Test
        fun `should roll back a consumed aggregate persisted in the surrounding transaction`() {
            val seed = "persist-rollback"
            val ready = pendingIntent(seed).markReady(METADATA, READY_AT)
            val error =
                assertThrows<IllegalStateException> {
                    runTest {
                        seedGroup(seed)
                        repository.persist(ready)
                        transactionRunner.transaction {
                            val stored = requireNotNull(repository.findByIdAndGroup(ready.id, ready.group))
                            repository.persist(stored.consume(CONSUMED_AT))
                            error("rollback consumption")
                        }
                    }
                }

            assertEquals("rollback consumption", error.message)
            runTest {
                assertIntentEquals(ready, repository.findByIdAndGroup(ready.id, ready.group))
            }
        }
    }

    @Nested
    inner class PersistAll {
        @Test
        fun `should persist multiple consumed upload intents atomically`() =
            runTest {
                val group = seedGroup("success-persist-all")
                val first =
                    pendingIntent(
                        seed = "success-persist-all",
                        id = DocumentUploadIntentId(testUuid("success-first:intent")),
                        documentSeed = "success-first",
                    ).markReady(METADATA, READY_AT)
                val second =
                    pendingIntent(
                        seed = "success-persist-all",
                        id = DocumentUploadIntentId(testUuid("success-second:intent")),
                        documentSeed = "success-second",
                    ).markReady(METADATA, READY_AT)
                repository.persistAll(listOf(first, second))

                val consumed = listOf(first.consume(CONSUMED_AT), second.consume(CONSUMED_AT))
                repository.persistAll(consumed)

                assertIntentsEqual(
                    consumed,
                    consumed.map { repository.findByIdAndGroup(it.id, group) },
                )
            }

        @Test
        fun `should reject duplicate upload intent identifiers before persisting`() =
            runTest {
                val duplicate = pendingIntent("duplicate-persist-all")

                val error =
                    assertThrows<IllegalArgumentException> {
                        repository.persistAll(listOf(duplicate, duplicate))
                    }

                assertEquals("document upload intent identifiers must be unique", error.message)
            }

        @Test
        fun `should roll back the whole batch when one consumed transition is stale`() =
            runTest {
                val group = seedGroup("stale-persist-all")
                val first =
                    pendingIntent(
                        seed = "stale-persist-all",
                        id = DocumentUploadIntentId(testUuid("stale-first:intent")),
                        documentSeed = "stale-first",
                    ).markReady(METADATA, READY_AT)
                val second =
                    pendingIntent(
                        seed = "stale-persist-all",
                        id = DocumentUploadIntentId(testUuid("stale-second:intent")),
                        documentSeed = "stale-second",
                    ).markReady(METADATA, READY_AT)
                repository.persistAll(listOf(first, second))
                repository.persist(second.consume(CONSUMED_AT))

                val error =
                    assertThrows<IllegalStateException> {
                        repository.persistAll(listOf(first.consume(CONSUMED_AT), second.consume(CONSUMED_AT)))
                    }

                assertEquals("document upload intent persistence must affect exactly the requested rows", error.message)
                assertIntentEquals(first, repository.findByIdAndGroup(first.id, group))
                assertIntentEquals(second.consume(CONSUMED_AT), repository.findByIdAndGroup(second.id, group))
            }
    }

    @Nested
    inner class FindByIdAndGroup {
        @Test
        fun `should return null when the upload intent is missing`() =
            runTest {
                val group = seedGroup("missing")

                assertNull(repository.findByIdAndGroup(DocumentUploadIntentId(testUuid("dui:missing")), group))
            }

        @Test
        fun `should not expose an upload intent from another group`() =
            runTest {
                val stored = pendingIntent("source")
                seedGroup("source")
                val otherGroup = seedGroup("other")
                repository.persist(stored)

                assertNull(repository.findByIdAndGroup(stored.id, otherGroup))
            }
    }

    @Nested
    inner class FindReadyByIdsAndGroupAndUploader {
        @Test
        fun `should return an empty list without querying when no identifiers are requested`() =
            runTest {
                val group = seedGroup("batch-empty")

                assertEquals(
                    emptyList<DocumentUploadIntent>(),
                    repository.findReadyByIdsAndGroupAndUploader(emptySet(), group, memberEmail("batch-empty-uploader")),
                )
            }

        @Test
        fun `should return upload intents in stable identifier order`() =
            runTest {
                val group = seedGroup("batch-order")
                val first =
                    pendingIntent(
                        seed = "batch-order",
                        id = DocumentUploadIntentId(testUuid("order-first:intent")),
                        documentSeed = "first",
                    ).markReady(METADATA, READY_AT)
                val second =
                    pendingIntent(
                        seed = "batch-order",
                        id = DocumentUploadIntentId(testUuid("order-second:intent")),
                        documentSeed = "second",
                    ).markReady(METADATA, READY_AT)
                repository.persist(first)
                repository.persist(second)

                assertIntentsEqual(
                    listOf(first, second).sortedBy { it.id.toPrimitive() },
                    repository.findReadyByIdsAndGroupAndUploader(
                        setOf(second.id, first.id),
                        group,
                        memberEmail("batch-order-uploader"),
                    ),
                )
            }

        @Test
        fun `should only return ready upload intents belonging to the group and uploader`() =
            runTest {
                val sourceGroup = seedGroup("batch-source")
                val otherGroup = seedGroup("batch-other")
                val otherUploader = memberEmail("batch-other-uploader")
                memberRepository.persist(Member(otherUploader, CREATED_AT))
                groupRepository.persist(
                    requireNotNull(groupRepository.findById(sourceGroup)).addMember(otherUploader, CREATED_AT),
                )
                val ready = pendingIntent("batch-source", documentSeed = "ready").markReady(METADATA, READY_AT)
                val crossGroup =
                    pendingIntent(
                        seed = "batch-other",
                        id = DocumentUploadIntentId(testUuid("cross-group:dui:batch")),
                        documentSeed = "cross-group",
                    ).markReady(METADATA, READY_AT)
                val wrongUploader =
                    pendingIntent(
                        seed = "batch-source",
                        id = DocumentUploadIntentId(testUuid("wrong-uploader:dui:batch")),
                        uploader = otherUploader,
                        documentSeed = "wrong-uploader",
                    ).markReady(METADATA, READY_AT)
                val pending =
                    pendingIntent(
                        seed = "batch-source",
                        id = DocumentUploadIntentId(testUuid("pending:dui:batch-source")),
                        documentSeed = "pending",
                    )
                val consumed =
                    pendingIntent(
                        seed = "batch-source",
                        id = DocumentUploadIntentId(testUuid("consumed:dui:batch-source")),
                        documentSeed = "consumed",
                    ).markReady(METADATA, READY_AT)
                        .consume(CONSUMED_AT)
                listOf(ready, crossGroup, wrongUploader, pending, consumed).forEach { intent -> repository.persist(intent) }

                assertIntentsEqual(
                    listOf(ready),
                    repository.findReadyByIdsAndGroupAndUploader(
                        setOf(ready.id, crossGroup.id, wrongUploader.id, pending.id, consumed.id),
                        sourceGroup,
                        memberEmail("batch-source-uploader"),
                    ),
                )
            }
    }

    @Nested
    inner class FindConsumedByIdsAndGroup {
        @Test
        fun `should return consumed upload intents in stable identifier order`() =
            runTest {
                val seed = "c1a-order"
                val group = seedGroup(seed)
                val first =
                    pendingIntent(
                        seed = seed,
                        id = DocumentUploadIntentId(testUuid("0000000000000001")),
                        documentSeed = "first",
                    ).markReady(METADATA, READY_AT)
                        .consume(CONSUMED_AT)
                val second =
                    pendingIntent(
                        seed = seed,
                        id = DocumentUploadIntentId(testUuid("0000000000000002")),
                        documentSeed = "second",
                    ).markReady(METADATA, READY_AT)
                        .consume(CONSUMED_AT)
                repository.persist(first)
                repository.persist(second)

                assertIntentsEqual(
                    listOf(first, second).sortedBy { it.id.toPrimitive() },
                    repository.findConsumedByIdsAndGroup(setOf(second.id, first.id), group),
                )
            }

        @Test
        fun `should return an empty list when no identifiers are requested`() =
            runTest {
                val group = seedGroup("c1b-empty")

                assertEquals(
                    emptyList<DocumentUploadIntent>(),
                    repository.findConsumedByIdsAndGroup(emptySet(), group),
                )
            }

        @Test
        fun `should exclude pending and ready upload intents`() =
            runTest {
                val seed = "c1c-status"
                val group = seedGroup(seed)
                val consumed =
                    pendingIntent(seed, documentSeed = "consumed")
                        .markReady(METADATA, READY_AT)
                        .consume(CONSUMED_AT)
                val pending =
                    pendingIntent(
                        seed = seed,
                        id = DocumentUploadIntentId(testUuid("c1pending-intent")),
                        documentSeed = "pending",
                    )
                val ready =
                    pendingIntent(
                        seed = seed,
                        id = DocumentUploadIntentId(testUuid("c1ready-intent!!")),
                        documentSeed = "ready",
                    ).markReady(METADATA, READY_AT)
                repository.persist(consumed)
                repository.persist(pending)
                repository.persist(ready)

                assertIntentsEqual(
                    listOf(consumed),
                    repository.findConsumedByIdsAndGroup(setOf(consumed.id, pending.id, ready.id), group),
                )
            }

        @Test
        fun `should exclude consumed upload intents from another group`() =
            runTest {
                val sourceSeed = "c1d-source"
                val otherSeed = "c1e-other"
                val sourceGroup = seedGroup(sourceSeed)
                val otherGroup = seedGroup(otherSeed)
                val source =
                    pendingIntent(sourceSeed, documentSeed = "source")
                        .markReady(METADATA, READY_AT)
                        .consume(CONSUMED_AT)
                val crossGroup =
                    pendingIntent(otherSeed, documentSeed = "cross-group")
                        .markReady(METADATA, READY_AT)
                        .consume(CONSUMED_AT)
                repository.persist(source)
                repository.persist(crossGroup)

                assertIntentsEqual(
                    listOf(source),
                    repository.findConsumedByIdsAndGroup(setOf(source.id, crossGroup.id), sourceGroup),
                )
                assertIntentsEqual(
                    listOf(crossGroup),
                    repository.findConsumedByIdsAndGroup(setOf(source.id, crossGroup.id), otherGroup),
                )
            }

        @Test
        fun `should return the available subset when requested identifiers are missing`() =
            runTest {
                val seed = "c1f-missing"
                val group = seedGroup(seed)
                val available =
                    pendingIntent(seed, documentSeed = "available")
                        .markReady(METADATA, READY_AT)
                        .consume(CONSUMED_AT)
                val missing = DocumentUploadIntentId(testUuid("c1missing-intent"))
                repository.persist(available)

                assertIntentsEqual(
                    listOf(available),
                    repository.findConsumedByIdsAndGroup(setOf(available.id, missing), group),
                )
            }
    }

    @Nested
    inner class FindPendingByIdAndGroupAndUploader {
        @Test
        fun `should only return the pending upload intent belonging to the group and uploader`() =
            runTest {
                val sourceGroup = seedGroup("pending-source")
                val otherGroup = seedGroup("pending-other")
                val otherUploader = memberEmail("pending-other-uploader")
                memberRepository.persist(Member(otherUploader, CREATED_AT))
                groupRepository.persist(
                    requireNotNull(groupRepository.findById(sourceGroup)).addMember(otherUploader, CREATED_AT),
                )
                val pending = pendingIntent("pending-source", documentSeed = "pending")
                val crossGroup =
                    pendingIntent(
                        seed = "pending-other",
                        id = DocumentUploadIntentId(testUuid("p8b-cross-group!")),
                        documentSeed = "cross-group",
                    )
                val wrongUploader =
                    pendingIntent(
                        seed = "pending-source",
                        id = DocumentUploadIntentId(testUuid("p8b-wrong-upldr!")),
                        uploader = otherUploader,
                        documentSeed = "wrong-uploader",
                    )
                val ready =
                    pendingIntent(
                        seed = "pending-source",
                        id = DocumentUploadIntentId(testUuid("p8b-ready-intent")),
                        documentSeed = "ready",
                    ).markReady(METADATA, READY_AT)
                val consumed =
                    pendingIntent(
                        seed = "pending-source",
                        id = DocumentUploadIntentId(testUuid("p8b-consumed-int")),
                        documentSeed = "consumed",
                    ).markReady(METADATA, READY_AT)
                        .consume(CONSUMED_AT)
                for (intent in listOf(pending, crossGroup, wrongUploader, ready, consumed)) {
                    repository.persist(intent)
                }

                assertEquals(
                    listOf(pending.toSnapshot(), null, null, null, null),
                    listOf(
                        repository.findPendingByIdAndGroupAndUploader(pending.id, sourceGroup, pending.uploader),
                        repository.findPendingByIdAndGroupAndUploader(crossGroup.id, sourceGroup, pending.uploader),
                        repository.findPendingByIdAndGroupAndUploader(wrongUploader.id, sourceGroup, pending.uploader),
                        repository.findPendingByIdAndGroupAndUploader(ready.id, sourceGroup, pending.uploader),
                        repository.findPendingByIdAndGroupAndUploader(consumed.id, sourceGroup, pending.uploader),
                    ).map { it?.toSnapshot() },
                )
            }
    }

    private suspend fun seedGroup(seed: String): GroupId {
        val uploader = memberEmail("$seed-uploader")
        val group = groupId(seed)
        memberRepository.persist(Member(uploader, CREATED_AT))
        groupRepository.persist(Group.create(group, uploader, CREATED_AT))
        return group
    }

    private fun pendingIntent(
        seed: String,
        id: DocumentUploadIntentId = DocumentUploadIntentId(testUuid("dui:$seed")),
        uploader: MemberEmail = memberEmail("$seed-uploader"),
        documentSeed: String = seed,
    ): DocumentUploadIntent =
        DocumentUploadIntent.create(
            id = id,
            group = groupId(seed),
            uploader = uploader,
            storageKey = DocumentStorageKey.of("groups/$seed/documents/$documentSeed.pdf"),
            fileName = DocumentFileName.of("Facture $documentSeed.pdf"),
            expectedMetadata = METADATA,
            createdAt = CREATED_AT,
            expiresAt = EXPIRES_AT,
        )

    private fun assertIntentEquals(
        expected: DocumentUploadIntent,
        actual: DocumentUploadIntent?,
    ) = assertEquals(expected.toSnapshot(), actual?.toSnapshot())

    private fun assertIntentsEqual(
        expected: List<DocumentUploadIntent>,
        actual: List<DocumentUploadIntent?>,
    ) = assertEquals(expected.map { it.toSnapshot() }, actual.map { it?.toSnapshot() })

    private fun persistenceFailure(intent: DocumentUploadIntent): PersistenceFailure {
        val error = runCatching { runTest { repository.persist(intent) } }.exceptionOrNull()

        return PersistenceFailure(error?.javaClass, error?.message)
    }

    private fun DocumentUploadIntent.toSnapshot() =
        DocumentUploadIntentSnapshot(
            id = id,
            group = group,
            uploader = uploader,
            storageKey = storageKey,
            fileName = fileName,
            expectedMetadata = expectedMetadata,
            createdAt = createdAt,
            expiresAt = expiresAt,
            status = status,
        )

    private data class DocumentUploadIntentSnapshot(
        val id: DocumentUploadIntentId,
        val group: GroupId,
        val uploader: MemberEmail,
        val storageKey: DocumentStorageKey,
        val fileName: DocumentFileName,
        val expectedMetadata: DocumentMetadata,
        val createdAt: Instant,
        val expiresAt: Instant,
        val status: DocumentUploadIntentStatus,
    )

    private data class PersistenceFailure(
        val type: Class<out Throwable>?,
        val message: String?,
    )

    private companion object {
        val PERSISTENCE_REJECTION =
            PersistenceFailure(
                type = IllegalStateException::class.java,
                message = "document upload intent persistence must affect exactly one row",
            )
        val CREATED_AT: Instant = Instant.parse("2026-08-16T10:00:00Z")
        val READY_AT: Instant = Instant.parse("2026-08-16T10:01:00Z")
        val CONSUMED_AT: Instant = Instant.parse("2026-08-16T10:02:00Z")
        val EXPIRES_AT: Instant = Instant.parse("2026-08-16T10:05:00Z")
        val METADATA =
            DocumentMetadata(
                mediaType = DocumentMediaType.of("application/pdf"),
                size = DocumentSize.ofBytes(512),
                checksum = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32))),
            )
    }
}
