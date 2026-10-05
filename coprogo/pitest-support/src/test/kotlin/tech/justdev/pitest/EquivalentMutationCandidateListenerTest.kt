package tech.justdev.pitest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.pitest.classinfo.ClassName
import org.pitest.mutationtest.ClassMutationResults
import org.pitest.mutationtest.DetectionStatus
import org.pitest.mutationtest.MutationResult
import org.pitest.mutationtest.MutationStatusTestPair
import org.pitest.mutationtest.engine.Location
import org.pitest.mutationtest.engine.MutationDetails
import org.pitest.mutationtest.engine.MutationIdentifier

class EquivalentMutationCandidateListenerTest {
    @Test
    fun `reports only surviving mutations as documented whitelist candidates`() {
        val output = StringBuilder()
        val listener = EquivalentMutationCandidateListener(output)
        listener.runStart()

        listener.handleMutationResult(
            ClassMutationResults(
                listOf(
                    mutationResult(DetectionStatus.KILLED, 11),
                    mutationResult(DetectionStatus.SURVIVED, 12),
                ),
            ),
        )
        listener.runEnd()

        assertEquals(
            """
            PIT survivors to review before updating pitest-equivalent-mutations.json (1):

            [1] example.Target
                method         : doWork
                source         : Example.kt:42
                mutator        : org.pitest.ExampleMutator
                bytecode index : 12
                mutation       : changed conditional boundary

            """.trimIndent(),
            output.toString(),
        )
    }

    private fun mutationResult(
        status: DetectionStatus,
        index: Int,
    ): MutationResult =
        MutationResult(
            MutationDetails(
                MutationIdentifier(
                    Location(ClassName.fromString("example.Target"), "doWork", "()V"),
                    index,
                    "org.pitest.ExampleMutator",
                ),
                "Example.kt",
                "changed conditional boundary",
                42,
                1,
            ),
            MutationStatusTestPair(1, status, emptyList(), emptyList(), emptyList()),
        )
}
