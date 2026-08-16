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
import tech.justdev.domain.shared.valueobject.GroupId
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

    @Nested
    inner class Persist {
        @Test
        fun `should round trip pending ready and consumed upload intents`() =
            runTest {
                listOf(
                    "pending" to { intent: DocumentUploadIntent -> intent },
                    "ready" to { intent: DocumentUploadIntent -> intent.markReady(METADATA, READY_AT) },
                    "consumed" to
                        { intent: DocumentUploadIntent ->
                            intent.markReady(METADATA, READY_AT).consume(CONSUMED_AT)
                        },
                ).forEach { (seed, transition) ->
                    seedGroup(seed)
                    val expected = transition(pendingIntent(seed))

                    repository.persist(expected)

                    assertIntentEquals(expected, repository.findByIdAndGroup(expected.id, expected.group))
                }
            }

        @Test
        fun `should update transitions for the same upload intent`() =
            runTest {
                val seed = "transitions"
                seedGroup(seed)
                val pending = pendingIntent(seed)
                val ready = pending.markReady(METADATA, READY_AT)
                val consumed = ready.consume(CONSUMED_AT)

                listOf(pending, ready, consumed).forEach { expected ->
                    repository.persist(expected)

                    assertIntentEquals(expected, repository.findByIdAndGroup(expected.id, expected.group))
                }
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

            listOf(pending, ready).forEach(::assertPersistenceRejected)

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

            assertPersistenceRejected(pending.markReady(METADATA, READY_AT.plusSeconds(1)))

            val consumed = ready.consume(CONSUMED_AT)
            runTest { repository.persist(consumed) }
            assertPersistenceRejected(ready.consume(CONSUMED_AT.plusSeconds(1)))

            runTest {
                assertIntentEquals(consumed, repository.findByIdAndGroup(consumed.id, consumed.group))
            }
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
    ): DocumentUploadIntent =
        DocumentUploadIntent.create(
            id = id,
            group = groupId(seed),
            uploader = memberEmail("$seed-uploader"),
            storageKey = DocumentStorageKey.of("groups/$seed/documents/invoice.pdf"),
            fileName = DocumentFileName.of("Facture $seed.pdf"),
            expectedMetadata = METADATA,
            createdAt = CREATED_AT,
            expiresAt = EXPIRES_AT,
        )

    private fun assertIntentEquals(
        expected: DocumentUploadIntent,
        actual: DocumentUploadIntent?,
    ) {
        requireNotNull(actual)
        assertEquals(expected.id, actual.id)
        assertEquals(expected.group, actual.group)
        assertEquals(expected.uploader, actual.uploader)
        assertEquals(expected.storageKey, actual.storageKey)
        assertEquals(expected.fileName, actual.fileName)
        assertEquals(expected.expectedMetadata, actual.expectedMetadata)
        assertEquals(expected.createdAt, actual.createdAt)
        assertEquals(expected.expiresAt, actual.expiresAt)
        assertEquals(expected.status, actual.status)
    }

    private fun assertPersistenceRejected(intent: DocumentUploadIntent) {
        val error = assertThrows<IllegalStateException> { runTest { repository.persist(intent) } }

        assertEquals("document upload intent persistence must affect exactly one row", error.message)
    }

    private companion object {
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
