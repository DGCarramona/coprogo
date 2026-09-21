package tech.justdev.domain.document.repository

import tech.justdev.domain.document.entity.DocumentUploadIntent
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId

interface DocumentUploadIntentRepository {
    suspend fun persist(intent: DocumentUploadIntent)

    suspend fun persistAll(intents: List<DocumentUploadIntent>)

    suspend fun findPendingByIdAndGroupAndUploader(
        id: DocumentUploadIntentId,
        group: GroupId,
        uploader: MemberEmail,
    ): DocumentUploadIntent?

    suspend fun findReadyByIdsAndGroupAndUploader(
        ids: Set<DocumentUploadIntentId>,
        group: GroupId,
        uploader: MemberEmail,
    ): List<DocumentUploadIntent>

    suspend fun findConsumedByIdsAndGroup(
        ids: Set<DocumentUploadIntentId>,
        group: GroupId,
    ): List<DocumentUploadIntent>

    suspend fun findByIdAndGroup(
        id: DocumentUploadIntentId,
        group: GroupId,
    ): DocumentUploadIntent?
}
