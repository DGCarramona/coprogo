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
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.Base64

class StartSupportingDocumentUploadUseCaseTest {
    @Nested
    inner class Invoke {
        @Test
        fun `should presign and persist a pending upload intent with the target expiry`() {
            runTest {
                val interactions = mutableListOf<String>()
                val intentId = DocumentUploadIntentId(testUuid("start-upload:intent"))
                val target = uploadTarget(expiresAt = CREATED_AT.plusSeconds(300))
                val storage = RecordingDocumentStorage(interactions, target)
                val repository = RecordingDocumentUploadIntentRepository(interactions)

                val result =
                    useCase(
                        groupRepository = RecordingGroupRepository(interactions, group()),
                        idGenerator =
                            DocumentUploadIntentIdGenerator {
                                interactions += "id"
                                intentId
                            },
                        storage = storage,
                        repository = repository,
                    )(command())

                assertEquals(
                    StartSupportingDocumentUploadResult(
                        intentId = intentId,
                        target = target,
                    ),
                    result,
                )
                assertEquals(
                    listOf(
                        DocumentUploadRequest(
                            key = DocumentStorageKey.of("groups/${GROUP.toPrimitive()}/documents/${intentId.toPrimitive()}"),
                            mediaType = METADATA.mediaType,
                            size = METADATA.size,
                            checksum = METADATA.checksum,
                            validFor = VALID_FOR,
                        ),
                    ),
                    storage.uploadRequests,
                )
                assertEquals(
                    listOf(
                        PersistedIntent(
                            id = intentId,
                            group = GROUP,
                            uploader = UPLOADER,
                            storageKey = DocumentStorageKey.of("groups/${GROUP.toPrimitive()}/documents/${intentId.toPrimitive()}"),
                            fileName = FILE_NAME,
                            metadata = METADATA,
                            createdAt = CREATED_AT,
                            expiresAt = target.expiresAt,
                            status = DocumentUploadIntentStatus.Pending,
                        ),
                    ),
                    repository.persisted.map(PersistedIntent::from),
                )
                assertEquals(listOf("membership", "id", "presign", "persist"), interactions)
            }
        }

        @Test
        fun `should check membership before generating an id, presigning or persisting`() {
            val interactions = mutableListOf<String>()
            val storage = RecordingDocumentStorage(interactions, uploadTarget(CREATED_AT.plusSeconds(300)))
            val repository = RecordingDocumentUploadIntentRepository(interactions)
            val useCase =
                useCase(
                    groupRepository = RecordingGroupRepository(interactions, group()),
                    idGenerator =
                        DocumentUploadIntentIdGenerator {
                            interactions += "id"
                            throw AssertionError("an upload intent id must not be generated before membership validation")
                        },
                    storage = storage,
                    repository = repository,
                )

            assertThrows<GroupAccessDeniedException> {
                runTest {
                    useCase(command(uploader = memberEmail("outsider")))
                }
            }

            assertEquals(listOf("membership"), interactions)
            assertEquals(emptyList<DocumentUploadRequest>(), storage.uploadRequests)
            assertEquals(emptyList<DocumentUploadIntent>(), repository.persisted)
        }

        @Test
        fun `should not persist an intent when presigning fails`() {
            val interactions = mutableListOf<String>()
            val repository = RecordingDocumentUploadIntentRepository(interactions)
            val useCase =
                useCase(
                    groupRepository = RecordingGroupRepository(interactions, group()),
                    idGenerator =
                        DocumentUploadIntentIdGenerator {
                            interactions += "id"
                            DocumentUploadIntentId(testUuid("presign-failure:intent"))
                        },
                    storage = FailingDocumentStorage(interactions),
                    repository = repository,
                )

            val error =
                assertThrows<IllegalStateException> {
                    runTest { useCase(command()) }
                }

            assertEquals("presigning failed", error.message)
            assertEquals(listOf("membership", "id", "presign"), interactions)
            assertEquals(emptyList<DocumentUploadIntent>(), repository.persisted)
        }

        @Test
        fun `should not persist an intent when the target expiry is not after creation`() {
            val interactions = mutableListOf<String>()
            val repository = RecordingDocumentUploadIntentRepository(interactions)
            val useCase =
                useCase(
                    groupRepository = RecordingGroupRepository(interactions, group()),
                    idGenerator =
                        DocumentUploadIntentIdGenerator {
                            interactions += "id"
                            DocumentUploadIntentId(testUuid("invalid-expiry:intent"))
                        },
                    storage = RecordingDocumentStorage(interactions, uploadTarget(CREATED_AT)),
                    repository = repository,
                )

            val error =
                assertThrows<IllegalArgumentException> {
                    runTest { useCase(command()) }
                }

            assertEquals("document upload intent expiry must be after creation", error.message)
            assertEquals(listOf("membership", "id", "presign"), interactions)
            assertEquals(emptyList<DocumentUploadIntent>(), repository.persisted)
        }
    }

    private fun useCase(
        groupRepository: GroupRepository,
        idGenerator: DocumentUploadIntentIdGenerator,
        storage: DocumentStorage,
        repository: DocumentUploadIntentRepository,
    ): StartSupportingDocumentUploadUseCase =
        StartSupportingDocumentUploadUseCaseImpl(
            groupAccessPolicy = GroupAccessPolicy(groupRepository),
            documentUploadIntentIdGenerator = idGenerator,
            documentStorage = storage,
            documentUploadIntentRepository = repository,
        )

    private fun command(uploader: MemberEmail = UPLOADER): StartSupportingDocumentUploadCommand =
        StartSupportingDocumentUploadCommand(
            group = GROUP,
            uploader = uploader,
            fileName = FILE_NAME,
            metadata = METADATA,
            createdAt = CREATED_AT,
            validFor = VALID_FOR,
        )

    private fun group(): Group =
        Group.create(
            id = GROUP,
            createdBy = UPLOADER,
            createdAt = CREATED_AT.minusSeconds(60),
        )

    private fun uploadTarget(expiresAt: Instant): DocumentUploadTarget =
        DocumentUploadTarget(
            uri = URI("https://storage.example.test/uploads/document"),
            requiredHeaders = mapOf("content-type" to "application/pdf"),
            expiresAt = expiresAt,
        )

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
        private val target: DocumentUploadTarget,
    ) : DocumentStorage {
        val uploadRequests = mutableListOf<DocumentUploadRequest>()

        override suspend fun presignUpload(request: DocumentUploadRequest): DocumentUploadTarget {
            interactions += "presign"
            uploadRequests += request
            return target
        }

        override suspend fun inspect(key: DocumentStorageKey): DocumentMetadata? = error("not used")

        override suspend fun presignDownload(request: DocumentDownloadRequest): DocumentDownloadTarget = error("not used")
    }

    private class FailingDocumentStorage(
        private val interactions: MutableList<String>,
    ) : DocumentStorage {
        override suspend fun presignUpload(request: DocumentUploadRequest): DocumentUploadTarget {
            interactions += "presign"
            throw IllegalStateException("presigning failed")
        }

        override suspend fun inspect(key: DocumentStorageKey): DocumentMetadata? = error("not used")

        override suspend fun presignDownload(request: DocumentDownloadRequest): DocumentDownloadTarget = error("not used")
    }

    private class RecordingDocumentUploadIntentRepository(
        private val interactions: MutableList<String>,
    ) : DocumentUploadIntentRepository {
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
        ): DocumentUploadIntent? = error("not used")

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

    private data class PersistedIntent(
        val id: DocumentUploadIntentId,
        val group: GroupId,
        val uploader: MemberEmail,
        val storageKey: DocumentStorageKey,
        val fileName: DocumentFileName,
        val metadata: DocumentMetadata,
        val createdAt: Instant,
        val expiresAt: Instant,
        val status: DocumentUploadIntentStatus,
    ) {
        companion object {
            fun from(intent: DocumentUploadIntent): PersistedIntent =
                PersistedIntent(
                    id = intent.id,
                    group = intent.group,
                    uploader = intent.uploader,
                    storageKey = intent.storageKey,
                    fileName = intent.fileName,
                    metadata = intent.expectedMetadata,
                    createdAt = intent.createdAt,
                    expiresAt = intent.expiresAt,
                    status = intent.status,
                )
        }
    }

    private companion object {
        val GROUP: GroupId = groupId("document-group")
        val UPLOADER: MemberEmail = memberEmail("alice")
        val FILE_NAME: DocumentFileName = DocumentFileName.of("invoice.pdf")
        val METADATA =
            DocumentMetadata(
                mediaType = DocumentMediaType.of("application/pdf"),
                size = DocumentSize.ofBytes(512),
                checksum = DocumentSha256.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32))),
            )
        val CREATED_AT: Instant = Instant.parse("2026-09-17T10:00:00Z")
        val VALID_FOR: Duration = Duration.ofMinutes(5)
    }
}
