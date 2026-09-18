package tech.justdev.application.document

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.application.group.GroupAccessDeniedException
import tech.justdev.application.group.GroupAccessPolicy
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
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.testUuid
import java.time.Instant
import java.util.Base64

class ConfirmSupportingDocumentUploadUseCaseTest {
    @Nested
    inner class Invoke {
        @Test
        fun `should inspect and persist the ready upload intent after membership validation`() =
            runTest {
                val interactions = mutableListOf<String>()
                val pending = pendingIntent()
                val repository = RecordingDocumentUploadIntentRepository(interactions, pending)
                val storage = RecordingDocumentStorage(interactions, METADATA)

                useCase(
                    groupRepository = RecordingGroupRepository(interactions, group()),
                    documentStorage = storage,
                    documentUploadIntentRepository = repository,
                )(command())

                assertEquals(listOf(PendingIntentLookup(INTENT, GROUP, UPLOADER)), repository.pendingLookups)
                assertEquals(listOf(pending.storageKey), storage.inspectedKeys)
                assertEquals(
                    listOf(
                        PersistedIntent(
                            id = INTENT,
                            status = DocumentUploadIntentStatus.Ready(VERIFIED_AT),
                        ),
                    ),
                    repository.persisted.map(PersistedIntent::from),
                )
                assertEquals(listOf("membership", "find", "inspect", "persist"), interactions)
            }

        @Test
        fun `should check membership before reading the upload intent`() {
            val interactions = mutableListOf<String>()
            val repository = RecordingDocumentUploadIntentRepository(interactions, pendingIntent())
            val storage = RecordingDocumentStorage(interactions, METADATA)

            assertThrows<GroupAccessDeniedException> {
                runTest {
                    useCase(
                        groupRepository = RecordingGroupRepository(interactions, group()),
                        documentStorage = storage,
                        documentUploadIntentRepository = repository,
                    )(command(uploader = memberEmail("outsider")))
                }
            }

            assertEquals(listOf("membership"), interactions)
            assertEquals(emptyList<PendingIntentLookup>(), repository.pendingLookups)
            assertEquals(emptyList<DocumentStorageKey>(), storage.inspectedKeys)
            assertEquals(emptyList<DocumentUploadIntent>(), repository.persisted)
        }

        @Test
        fun `should reject an unavailable pending upload intent without inspecting or persisting`() {
            val interactions = mutableListOf<String>()
            val repository = RecordingDocumentUploadIntentRepository(interactions, pending = null)
            val storage = RecordingDocumentStorage(interactions, METADATA)

            val error =
                assertThrows<DocumentUploadIntentUnavailableException> {
                    runTest {
                        useCase(
                            groupRepository = RecordingGroupRepository(interactions, group()),
                            documentStorage = storage,
                            documentUploadIntentRepository = repository,
                        )(command())
                    }
                }

            assertEquals("document upload intent is unavailable", error.message)
            assertEquals(listOf("membership", "find"), interactions)
            assertEquals(emptyList<DocumentStorageKey>(), storage.inspectedKeys)
            assertEquals(emptyList<DocumentUploadIntent>(), repository.persisted)
        }

        @Test
        fun `should reject a missing stored object without persisting`() {
            val interactions = mutableListOf<String>()
            val repository = RecordingDocumentUploadIntentRepository(interactions, pendingIntent())
            val storage = RecordingDocumentStorage(interactions, metadata = null)

            val error =
                assertThrows<DocumentUploadIntentUnavailableException> {
                    runTest {
                        useCase(
                            groupRepository = RecordingGroupRepository(interactions, group()),
                            documentStorage = storage,
                            documentUploadIntentRepository = repository,
                        )(command())
                    }
                }

            assertEquals("document upload intent is unavailable", error.message)
            assertEquals(listOf("membership", "find", "inspect"), interactions)
            assertEquals(emptyList<DocumentUploadIntent>(), repository.persisted)
        }

        @Test
        fun `should reject mismatched stored metadata without persisting`() {
            val interactions = mutableListOf<String>()
            val repository = RecordingDocumentUploadIntentRepository(interactions, pendingIntent())
            val storage = RecordingDocumentStorage(interactions, mismatchedMetadata())

            val error =
                assertThrows<DocumentUploadIntentUnavailableException> {
                    runTest {
                        useCase(
                            groupRepository = RecordingGroupRepository(interactions, group()),
                            documentStorage = storage,
                            documentUploadIntentRepository = repository,
                        )(command())
                    }
                }

            assertEquals("document upload intent is unavailable", error.message)
            assertEquals(listOf("membership", "find", "inspect"), interactions)
            assertEquals(emptyList<DocumentUploadIntent>(), repository.persisted)
        }

        @Test
        fun `should reject verification outside the upload intent lifetime without persisting`() {
            val interactions = mutableListOf<String>()
            val repository = RecordingDocumentUploadIntentRepository(interactions, pendingIntent(expiresAt = VERIFIED_AT))
            val storage = RecordingDocumentStorage(interactions, METADATA)

            val error =
                assertThrows<DocumentUploadIntentUnavailableException> {
                    runTest {
                        useCase(
                            groupRepository = RecordingGroupRepository(interactions, group()),
                            documentStorage = storage,
                            documentUploadIntentRepository = repository,
                        )(command())
                    }
                }

            assertEquals("document upload intent is unavailable", error.message)
            assertEquals(listOf("membership", "find", "inspect"), interactions)
            assertEquals(emptyList<DocumentUploadIntent>(), repository.persisted)
        }
    }

    private fun useCase(
        groupRepository: GroupRepository,
        documentStorage: DocumentStorage,
        documentUploadIntentRepository: DocumentUploadIntentRepository,
    ): ConfirmSupportingDocumentUploadUseCase =
        ConfirmSupportingDocumentUploadUseCaseImpl(
            groupAccessPolicy = GroupAccessPolicy(groupRepository),
            documentStorage = documentStorage,
            documentUploadIntentRepository = documentUploadIntentRepository,
        )

    private fun command(uploader: MemberEmail = UPLOADER): ConfirmSupportingDocumentUploadCommand =
        ConfirmSupportingDocumentUploadCommand(
            group = GROUP,
            uploader = uploader,
            intent = INTENT,
            verifiedAt = VERIFIED_AT,
        )

    private fun group(): Group = Group.create(GROUP, UPLOADER, CREATED_AT.minusSeconds(60))

    private fun pendingIntent(expiresAt: Instant = EXPIRES_AT): DocumentUploadIntent =
        DocumentUploadIntent.create(
            id = INTENT,
            group = GROUP,
            uploader = UPLOADER,
            storageKey = DocumentStorageKey.of("groups/${GROUP.toPrimitive()}/documents/${INTENT.toPrimitive()}"),
            fileName = DocumentFileName.of("invoice.pdf"),
            expectedMetadata = METADATA,
            createdAt = CREATED_AT,
            expiresAt = expiresAt,
        )

    private fun mismatchedMetadata(): DocumentMetadata = METADATA.copy(size = DocumentSize.ofBytes(METADATA.size.toBytes() + 1))

    private class RecordingGroupRepository(
        private val interactions: MutableList<String>,
        private val group: Group,
    ) : GroupRepository {
        override suspend fun findById(id: GroupId): Group? {
            interactions += "membership"
            return group.takeIf { it.id == id }
        }

        override suspend fun persist(group: Group) = Unit
    }

    private class RecordingDocumentStorage(
        private val interactions: MutableList<String>,
        private val metadata: DocumentMetadata?,
    ) : DocumentStorage {
        val inspectedKeys = mutableListOf<DocumentStorageKey>()

        override suspend fun presignUpload(request: DocumentUploadRequest): DocumentUploadTarget = error("not used")

        override suspend fun inspect(key: DocumentStorageKey): DocumentMetadata? {
            interactions += "inspect"
            inspectedKeys += key
            return metadata
        }

        override suspend fun presignDownload(request: DocumentDownloadRequest): DocumentDownloadTarget = error("not used")
    }

    private class RecordingDocumentUploadIntentRepository(
        private val interactions: MutableList<String>,
        private val pending: DocumentUploadIntent?,
    ) : DocumentUploadIntentRepository {
        val pendingLookups = mutableListOf<PendingIntentLookup>()
        val persisted = mutableListOf<DocumentUploadIntent>()

        override suspend fun persist(intent: DocumentUploadIntent) {
            interactions += "persist"
            persisted += intent
        }

        override suspend fun persistAll(intents: List<DocumentUploadIntent>) = error("not used")

        override suspend fun findPendingByIdAndGroupAndUploader(
            id: DocumentUploadIntentId,
            group: GroupId,
            uploader: MemberEmail,
        ): DocumentUploadIntent? {
            interactions += "find"
            pendingLookups += PendingIntentLookup(id, group, uploader)
            return pending
        }

        override suspend fun findReadyByIdsAndGroupAndUploader(
            ids: Set<DocumentUploadIntentId>,
            group: GroupId,
            uploader: MemberEmail,
        ): List<DocumentUploadIntent> = error("not used")

        override suspend fun findByIdAndGroup(
            id: DocumentUploadIntentId,
            group: GroupId,
        ): DocumentUploadIntent? = error("not used")
    }

    private data class PendingIntentLookup(
        val id: DocumentUploadIntentId,
        val group: GroupId,
        val uploader: MemberEmail,
    )

    private data class PersistedIntent(
        val id: DocumentUploadIntentId,
        val status: DocumentUploadIntentStatus,
    ) {
        companion object {
            fun from(intent: DocumentUploadIntent): PersistedIntent = PersistedIntent(intent.id, intent.status)
        }
    }

    private companion object {
        val GROUP: GroupId = groupId("confirm-document-group")
        val UPLOADER: MemberEmail = memberEmail("alice")
        val INTENT: DocumentUploadIntentId = DocumentUploadIntentId(testUuid("confirm-document:intent"))
        val CREATED_AT: Instant = Instant.parse("2026-09-18T10:00:00Z")
        val VERIFIED_AT: Instant = Instant.parse("2026-09-18T10:01:00Z")
        val EXPIRES_AT: Instant = Instant.parse("2026-09-18T10:05:00Z")
        val METADATA =
            DocumentMetadata(
                mediaType = DocumentMediaType.of("application/pdf"),
                size = DocumentSize.ofBytes(512),
                checksum = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32))),
            )
    }
}
