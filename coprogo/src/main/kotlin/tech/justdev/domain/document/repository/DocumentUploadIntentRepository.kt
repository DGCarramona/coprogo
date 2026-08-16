package tech.justdev.domain.document.repository

import tech.justdev.domain.document.entity.DocumentUploadIntent
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.shared.valueobject.GroupId

interface DocumentUploadIntentRepository {
    suspend fun persist(intent: DocumentUploadIntent)

    suspend fun findByIdAndGroup(
        id: DocumentUploadIntentId,
        group: GroupId,
    ): DocumentUploadIntent?
}
