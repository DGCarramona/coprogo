package tech.justdev.pitest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class EquivalentMutationWhitelistTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `loads documented exact mutation signatures from configured file`() {
        val whitelistFile = temporaryDirectory.resolve("equivalent-mutations.json")
        Files.writeString(whitelistFile, validWhitelist)

        val whitelist = EquivalentMutationWhitelist.load(whitelistFile)

        assertEquals(
            listOf(
                EquivalentMutationRule(
                    signature = MutationSignature("example.Class", "method", "example.Mutator", 42),
                    justification = "Equivalent compiler-generated branch.",
                ),
            ),
            whitelist.rules,
        )
    }

    @Test
    fun `rejects malformed duplicate or undocumented entries`() {
        val invalidWhitelists =
            listOf(
                """[{"className":"a"}]""",
                """[{"className":"a","method":"b","mutator":"c","instructionIndex":"one","justification":"reason"}]""",
                """[{"className":"a","method":"b","mutator":"c","instructionIndex":1,"justification":""}]""",
                """
                [
                    {"className":"a","method":"b","mutator":"c","instructionIndex":1,"justification":"reason"},
                    {"className":"a","method":"b","mutator":"c","instructionIndex":1,"justification":"another reason"}
                ]
                """.trimIndent(),
            )

        val errors =
            invalidWhitelists.map { json ->
                assertThrows(IllegalArgumentException::class.java) {
                    EquivalentMutationWhitelist.parse(json)
                }.message
            }

        assertEquals(4, errors.size)
    }

    private companion object {
        val validWhitelist =
            """
            [
              {
                "className": "example.Class",
                "method": "method",
                "mutator": "example.Mutator",
                "instructionIndex": 42,
                "justification": "Equivalent compiler-generated branch."
              }
            ]
            """.trimIndent()
    }
}
