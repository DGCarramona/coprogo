package tech.justdev.pitest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class EquivalentMutationWhitelistTest {
    @Test
    fun `loads documented exact mutation signatures`() {
        val whitelist = EquivalentMutationWhitelist.load()

        assertEquals(7, whitelist.rules.size)
        assertEquals(
            "minByOrNull compares two unique MemberEmail values, so equality is impossible.",
            whitelist.rules.single { rule -> rule.signature.instructionIndex == 463 }.justification,
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
}
