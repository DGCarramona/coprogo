package tech.justdev.pitest

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

internal class EquivalentMutationWhitelist private constructor(
    val rules: List<EquivalentMutationRule>,
) {
    private val signatures = rules.map(EquivalentMutationRule::signature).toSet()

    operator fun contains(signature: MutationSignature): Boolean = signature in signatures

    internal companion object {
        private const val RESOURCE = "/pitest-equivalent-mutations.json"
        private val objectMapper = ObjectMapper()
        private val requiredFields = setOf("className", "method", "mutator", "instructionIndex", "justification")

        fun load(): EquivalentMutationWhitelist =
            checkNotNull(EquivalentMutationWhitelist::class.java.getResourceAsStream(RESOURCE)) {
                "Missing equivalent-mutation whitelist resource $RESOURCE"
            }.bufferedReader().use { reader -> parse(reader.readText()) }

        fun parse(json: String): EquivalentMutationWhitelist {
            val root =
                runCatching { objectMapper.readTree(json) }
                    .getOrElse { cause -> throw IllegalArgumentException("Invalid equivalent-mutation whitelist JSON", cause) }
            require(root.isArray) { "Equivalent-mutation whitelist must be a JSON array" }

            val rules = root.mapIndexed(::parseRule)
            require(rules.map(EquivalentMutationRule::signature).toSet().size == rules.size) {
                "Equivalent-mutation whitelist contains duplicate signatures"
            }
            return EquivalentMutationWhitelist(rules)
        }

        private fun parseRule(
            index: Int,
            node: JsonNode,
        ): EquivalentMutationRule {
            require(node.isObject) { "Whitelist entry $index must be a JSON object" }
            require(node.fieldNames().asSequence().toSet() == requiredFields) {
                "Whitelist entry $index must contain exactly ${requiredFields.sorted()}"
            }

            val instructionIndex = node.required("instructionIndex")
            require(instructionIndex.isIntegralNumber && instructionIndex.canConvertToInt()) {
                "Whitelist entry $index instructionIndex must be an integer"
            }
            require(instructionIndex.intValue() >= 0) { "Whitelist entry $index instructionIndex must not be negative" }

            return EquivalentMutationRule(
                signature =
                    MutationSignature(
                        className = node.requiredText("className", index),
                        method = node.requiredText("method", index),
                        mutator = node.requiredText("mutator", index),
                        instructionIndex = instructionIndex.intValue(),
                    ),
                justification = node.requiredText("justification", index),
            )
        }

        private fun JsonNode.requiredText(
            field: String,
            entryIndex: Int,
        ): String {
            val value = required(field)
            require(value.isTextual && value.textValue().isNotBlank()) {
                "Whitelist entry $entryIndex $field must be a non-blank string"
            }
            return value.textValue()
        }
    }
}

internal data class EquivalentMutationRule(
    val signature: MutationSignature,
    val justification: String,
)

internal data class MutationSignature(
    val className: String,
    val method: String,
    val mutator: String,
    val instructionIndex: Int,
)
