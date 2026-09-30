package tech.justdev.pitest

import org.pitest.mutationtest.ClassMutationResults
import org.pitest.mutationtest.DetectionStatus
import org.pitest.mutationtest.MutationResultListener
import org.pitest.mutationtest.engine.MutationDetails

internal class EquivalentMutationCandidateListener(
    private val output: Appendable,
) : MutationResultListener {
    private val candidates = mutableMapOf<MutationSignature, MutationCandidate>()

    override fun runStart() {
        candidates.clear()
    }

    override fun handleMutationResult(results: ClassMutationResults) {
        results.mutations
            .filter { mutation -> mutation.status == DetectionStatus.SURVIVED }
            .forEach { mutation ->
                val details = mutation.details
                candidates[details.toSignature()] =
                    MutationCandidate(
                        signature = details.toSignature(),
                        source = "${details.filename}:${details.lineNumber}",
                        description = details.description,
                    )
            }
    }

    override fun runEnd() {
        if (candidates.isEmpty()) {
            return
        }

        val candidateOrder =
            compareBy<MutationCandidate> { candidate -> candidate.signature.className }
                .thenBy { candidate -> candidate.signature.method }
                .thenBy { candidate -> candidate.signature.instructionIndex }
        val formattedCandidates =
            candidates.values
                .sortedWith(candidateOrder)
                .mapIndexed { index, candidate -> candidate.format(index + 1) }
                .joinToString(separator = "\n\n")

        output
            .appendLine("PIT survivors to review before updating pitest-equivalent-mutations.json (${candidates.size}):")
            .appendLine()
            .appendLine(formattedCandidates)
    }

    private fun MutationDetails.toSignature(): MutationSignature =
        MutationSignature(
            className = className.asJavaName(),
            method = method,
            mutator = mutator,
            instructionIndex = firstIndex,
        )
}

private data class MutationCandidate(
    val signature: MutationSignature,
    val source: String,
    val description: String,
) {
    fun format(number: Int): String =
        """
        [$number] ${signature.className}
            method         : ${signature.method}
            source         : $source
            mutator        : ${signature.mutator}
            bytecode index : ${signature.instructionIndex}
            mutation       : $description
        """.trimIndent()
}
