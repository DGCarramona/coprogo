package tech.justdev.domain.document.entity

import tech.justdev.domain.document.valueobject.DocumentFileName
import tech.justdev.domain.document.valueobject.DocumentStorageKey
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.document.valueobject.SupportingDocumentAttachmentDeletion
import tech.justdev.domain.group.valueobject.MemberEmail
import java.time.Instant

class ExpenseSupportingDocument private constructor(
    val sourceUploadIntent: DocumentUploadIntentId,
    val uploader: MemberEmail,
    val storageKey: DocumentStorageKey,
    val fileName: DocumentFileName,
    val metadata: DocumentMetadata,
    val attachedAt: Instant,
    val replacesSourceUploadIntent: DocumentUploadIntentId?,
    val deletion: SupportingDocumentAttachmentDeletion?,
) {
    override fun equals(other: Any?): Boolean =
        (this === other) ||
            (
                (other is ExpenseSupportingDocument) &&
                    (sourceUploadIntent == other.sourceUploadIntent) &&
                    (uploader == other.uploader) &&
                    (storageKey == other.storageKey) &&
                    (fileName == other.fileName) &&
                    (metadata == other.metadata) &&
                    (attachedAt == other.attachedAt) &&
                    (replacesSourceUploadIntent == other.replacesSourceUploadIntent) &&
                    (deletion == other.deletion)
            )

    override fun hashCode(): Int =
        listOf(
            sourceUploadIntent,
            uploader,
            storageKey,
            fileName,
            metadata,
            attachedAt,
            replacesSourceUploadIntent,
            deletion,
        ).hashCode()

    fun replaceWith(consumedIntent: DocumentUploadIntent): ExpenseSupportingDocument {
        require(deletion == null) { "deleted supporting document cannot be replaced" }

        return fromConsumedUploadIntent(consumedIntent, replacesSourceUploadIntent = sourceUploadIntent)
            .also { replacement ->
                require(replacement.attachedAt >= attachedAt) {
                    "supporting document replacement must not precede the source attachment"
                }
            }
    }

    fun delete(
        by: MemberEmail,
        at: Instant,
    ): ExpenseSupportingDocument {
        require(deletion == null) { "supporting document has already been deleted" }
        require(at >= attachedAt) { "supporting document deletion must not precede its attachment" }

        return restore(
            sourceUploadIntent = sourceUploadIntent,
            uploader = uploader,
            storageKey = storageKey,
            fileName = fileName,
            metadata = metadata,
            attachedAt = attachedAt,
            replacesSourceUploadIntent = replacesSourceUploadIntent,
            deletion = SupportingDocumentAttachmentDeletion(by, at),
        )
    }

    companion object {
        fun restore(
            sourceUploadIntent: DocumentUploadIntentId,
            uploader: MemberEmail,
            storageKey: DocumentStorageKey,
            fileName: DocumentFileName,
            metadata: DocumentMetadata,
            attachedAt: Instant,
            replacesSourceUploadIntent: DocumentUploadIntentId? = null,
            deletion: SupportingDocumentAttachmentDeletion? = null,
        ): ExpenseSupportingDocument {
            require(sourceUploadIntent != replacesSourceUploadIntent) {
                "replaced and replacement documents must use distinct upload intents"
            }
            require(deletion?.deletedAt?.let { it >= attachedAt } != false) {
                "supporting document deletion must not precede its attachment"
            }

            return ExpenseSupportingDocument(
                sourceUploadIntent = sourceUploadIntent,
                uploader = uploader,
                storageKey = storageKey,
                fileName = fileName,
                metadata = metadata,
                attachedAt = attachedAt,
                replacesSourceUploadIntent = replacesSourceUploadIntent,
                deletion = deletion,
            )
        }

        fun fromConsumedUploadIntent(intent: DocumentUploadIntent): ExpenseSupportingDocument =
            fromConsumedUploadIntent(intent, replacesSourceUploadIntent = null)

        private fun fromConsumedUploadIntent(
            intent: DocumentUploadIntent,
            replacesSourceUploadIntent: DocumentUploadIntentId?,
        ): ExpenseSupportingDocument =
            when (val status = intent.status) {
                is DocumentUploadIntentStatus.Consumed ->
                    restore(
                        sourceUploadIntent = intent.id,
                        uploader = intent.uploader,
                        storageKey = intent.storageKey,
                        fileName = intent.fileName,
                        metadata = intent.expectedMetadata,
                        attachedAt = status.consumedAt,
                        replacesSourceUploadIntent = replacesSourceUploadIntent,
                    )

                DocumentUploadIntentStatus.Pending,
                is DocumentUploadIntentStatus.Ready,
                -> throw IllegalArgumentException("expense supporting document requires a consumed upload intent")
            }
    }
}
