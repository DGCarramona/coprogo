package tech.justdev.domain.document.entity

import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.expense.exception.ExpenseSupportingDocumentUnavailableException
import tech.justdev.domain.group.valueobject.MemberEmail
import java.time.Instant

data class ExpenseSupportingDocumentsReplacement(
    val supportingDocuments: ExpenseSupportingDocuments,
    val replacement: ExpenseSupportingDocument,
)

data class ExpenseSupportingDocumentsDeletion(
    val supportingDocuments: ExpenseSupportingDocuments,
    val deleted: ExpenseSupportingDocument,
)

class ExpenseSupportingDocuments private constructor(
    private val documents: List<ExpenseSupportingDocument>,
) {
    init {
        require(documents.map(ExpenseSupportingDocument::sourceUploadIntent).distinct().size == documents.size) {
            "supporting documents must use unique upload intents"
        }
        require(
            documents
                .mapNotNull(ExpenseSupportingDocument::replacesSourceUploadIntent)
                .all(documents.map(ExpenseSupportingDocument::sourceUploadIntent).toSet()::contains),
        ) {
            "supporting document replacements must reference an existing document"
        }
        require(
            documents
                .mapNotNull(ExpenseSupportingDocument::replacesSourceUploadIntent)
                .groupingBy { it }
                .eachCount()
                .values
                .all { it == 1 },
        ) {
            "supporting documents can only be replaced once"
        }
        require(!hasReplacementCycle(documents)) {
            "supporting document replacement history must not contain cycles"
        }
        require(
            documents
                .filter { it.deletion != null }
                .none { deleted -> documents.any { it.replacesSourceUploadIntent == deleted.sourceUploadIntent } },
        ) {
            "a deleted supporting document cannot have a replacement"
        }
        require(
            documents
                .filter { it.replacesSourceUploadIntent != null }
                .all { replacement ->
                    replacement.attachedAt >=
                        documents
                            .first { it.sourceUploadIntent == replacement.replacesSourceUploadIntent }
                            .attachedAt
                },
        ) {
            "supporting document replacement must not precede the source attachment"
        }
    }

    val all: List<ExpenseSupportingDocument>
        get() = documents

    val current: List<ExpenseSupportingDocument>
        get() {
            val replacedSources = documents.mapNotNull(ExpenseSupportingDocument::replacesSourceUploadIntent).toSet()

            return documents.filter { document ->
                document.sourceUploadIntent !in replacedSources && document.deletion == null
            }
        }

    val isEmpty: Boolean
        get() = documents.isEmpty()

    override fun equals(other: Any?): Boolean =
        (this === other) ||
            (
                (other is ExpenseSupportingDocuments) &&
                    (documents == other.documents)
            )

    override fun hashCode(): Int = documents.hashCode()

    override fun toString(): String = documents.toString()

    fun replace(
        sourceUploadIntent: DocumentUploadIntentId,
        consumedIntent: DocumentUploadIntent,
    ): ExpenseSupportingDocumentsReplacement =
        current
            .find { it.sourceUploadIntent == sourceUploadIntent }
            ?.replaceWith(consumedIntent)
            ?.let { replacement ->
                ExpenseSupportingDocumentsReplacement(
                    supportingDocuments = restore(documents + replacement),
                    replacement = replacement,
                )
            } ?: throw ExpenseSupportingDocumentUnavailableException()

    fun delete(
        sourceUploadIntent: DocumentUploadIntentId,
        by: MemberEmail,
        at: Instant,
    ): ExpenseSupportingDocumentsDeletion {
        val deleted =
            current
                .find { it.sourceUploadIntent == sourceUploadIntent }
                ?.delete(by = by, at = at)
                ?: throw ExpenseSupportingDocumentUnavailableException()

        return ExpenseSupportingDocumentsDeletion(
            supportingDocuments =
                restore(
                    documents.map { document ->
                        if (document.sourceUploadIntent == sourceUploadIntent) deleted else document
                    },
                ),
            deleted = deleted,
        )
    }

    companion object {
        fun empty(): ExpenseSupportingDocuments = ExpenseSupportingDocuments(emptyList())

        fun from(documents: List<ExpenseSupportingDocument>): ExpenseSupportingDocuments = ExpenseSupportingDocuments(documents.toList())

        fun restore(documents: List<ExpenseSupportingDocument>): ExpenseSupportingDocuments = from(documents)
    }
}

private fun hasReplacementCycle(documents: List<ExpenseSupportingDocument>): Boolean {
    val replacedBySource = documents.associate { it.sourceUploadIntent to it.replacesSourceUploadIntent }

    return documents.any { document ->
        generateSequence(document.sourceUploadIntent, replacedBySource::getValue)
            .take(documents.size + 1)
            .count() > documents.size
    }
}
