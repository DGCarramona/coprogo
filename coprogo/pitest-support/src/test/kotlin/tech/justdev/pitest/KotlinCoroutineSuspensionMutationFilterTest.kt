package tech.justdev.pitest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.JumpInsnNode
import org.pitest.bytecode.analysis.ClassTree
import org.pitest.classinfo.ClassName
import org.pitest.mutationtest.engine.Location
import org.pitest.mutationtest.engine.MutationDetails
import org.pitest.mutationtest.engine.MutationIdentifier

class KotlinCoroutineSuspensionMutationFilterTest {
    @Test
    fun `filters coroutine suspension checks while retaining source conditionals`() {
        val clazz = CoroutineMutationFixture::class.java
        val classTree = clazz.asClassTree()
        val method = classTree.rawNode().methods.single { it.name == "execute" }
        val instructions = method.instructions.toArray().toList()
        val suspensionCheck =
            instructions
                .withIndex()
                .single { (_, instruction) -> instruction.opcode == Opcodes.IF_ACMPNE }
        val sourceConditional =
            instructions
                .withIndex()
                .single { (_, instruction) -> instruction is JumpInsnNode && instruction.opcode == Opcodes.IF_ICMPLE }
        val mutations =
            listOf(suspensionCheck, sourceConditional).map { indexedInstruction ->
                MutationDetails(
                    MutationIdentifier(
                        Location(ClassName.fromClass(clazz), method.name, method.desc),
                        indexedInstruction.index + 1,
                        NEGATE_CONDITIONALS_MUTATOR,
                    ),
                    "KotlinCoroutineSuspensionMutationFilterTest.kt",
                    "negated conditional",
                    0,
                    0,
                )
            }
        val filter = KotlinCoroutineSuspensionMutationFilter()

        filter.begin(classTree)
        val remaining = filter.intercept(mutations, null)
        filter.end()

        assertEquals(listOf(sourceConditional.index + 1), remaining.map(MutationDetails::getFirstIndex))
    }

    private fun Class<*>.asClassTree(): ClassTree =
        ClassTree.fromBytes(
            checkNotNull(getResourceAsStream("/${name.replace('.', '/')}.class"))
                .use { stream -> stream.readAllBytes() },
        )

    private fun interface CoroutinePort {
        suspend fun load(): String
    }

    private class CoroutineMutationFixture {
        suspend fun execute(port: CoroutinePort): String {
            val value = port.load()
            return if (value.length > 3) value else "short"
        }
    }

    private companion object {
        const val NEGATE_CONDITIONALS_MUTATOR =
            "org.pitest.mutationtest.engine.gregor.mutators.NegateConditionalsMutator"
    }
}
