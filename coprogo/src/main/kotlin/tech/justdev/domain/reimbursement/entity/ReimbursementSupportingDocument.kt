package tech.justdev.domain.reimbursement.entity

import tech.justdev.domain.document.entity.DocumentMetadata
import tech.justdev.domain.document.entity.DocumentUploadIntent
import tech.justdev.domain.document.entity.DocumentUploadIntentStatus
import tech.justdev.domain.document.valueobject.DocumentFileName
import tech.justdev.domain.document.valueobject.DocumentStorageKey
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId
import java.time.Instant

data class ReimbursementSupportingDocument private constructor(
    val sourceUploadIntent: DocumentUploadIntentId,
    val group: GroupId,
    val uploader: MemberEmail,
    val storageKey: DocumentStorageKey,
    val fileName: DocumentFileName,
    val metadata: DocumentMetadata,
    val attachedAt: Instant,
) {
    companion object {
        fun restore(
            sourceUploadIntent: DocumentUploadIntentId,
            group: GroupId,
            uploader: MemberEmail,
            storageKey: DocumentStorageKey,
            fileName: DocumentFileName,
            metadata: DocumentMetadata,
            attachedAt: Instant,
        ): ReimbursementSupportingDocument =
            ReimbursementSupportingDocument(
                sourceUploadIntent = sourceUploadIntent,
                group = group,
                uploader = uploader,
                storageKey = storageKey,
                fileName = fileName,
                metadata = metadata,
                attachedAt = attachedAt,
            )

        fun fromConsumedUploadIntent(intent: DocumentUploadIntent): ReimbursementSupportingDocument =
            when (val status = intent.status) {
                is DocumentUploadIntentStatus.Consumed ->
                    restore(
                        sourceUploadIntent = intent.id,
                        group = intent.group,
                        uploader = intent.uploader,
                        storageKey = intent.storageKey,
                        fileName = intent.fileName,
                        metadata = intent.expectedMetadata,
                        attachedAt = status.consumedAt,
                    )

                DocumentUploadIntentStatus.Pending,
                is DocumentUploadIntentStatus.Ready,
                -> throw IllegalArgumentException("documented reimbursement supporting document requires a consumed upload intent")
            }
    }
}
