package tech.justdev.application.document

import jakarta.inject.Singleton
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import java.util.UUID

fun interface DocumentUploadIntentIdGenerator {
    fun next(): DocumentUploadIntentId
}

@Singleton
class RandomDocumentUploadIntentIdGenerator : DocumentUploadIntentIdGenerator {
    override fun next(): DocumentUploadIntentId = DocumentUploadIntentId(UUID.randomUUID())
}
