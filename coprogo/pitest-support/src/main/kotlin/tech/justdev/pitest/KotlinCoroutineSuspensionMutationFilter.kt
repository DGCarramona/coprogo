package tech.justdev.pitest

import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.MethodInsnNode
import org.pitest.bytecode.analysis.InstructionMatchers.aVariableAccess
import org.pitest.bytecode.analysis.InstructionMatchers.anyInstruction
import org.pitest.bytecode.analysis.InstructionMatchers.isA
import org.pitest.bytecode.analysis.InstructionMatchers.notAnInstruction
import org.pitest.bytecode.analysis.InstructionMatchers.variableMatches
import org.pitest.bytecode.analysis.MethodTree
import org.pitest.bytecode.analysis.OpcodeMatchers.ALOAD
import org.pitest.bytecode.analysis.OpcodeMatchers.ARETURN
import org.pitest.bytecode.analysis.OpcodeMatchers.DUP
import org.pitest.bytecode.analysis.OpcodeMatchers.IF_ACMPNE
import org.pitest.mutationtest.build.intercept.MutatorSpecificInterceptor
import org.pitest.mutationtest.build.intercept.Region
import org.pitest.mutationtest.engine.gregor.mutators.NegateConditionalsMutator
import org.pitest.sequence.Context
import org.pitest.sequence.Match
import org.pitest.sequence.QueryParams
import org.pitest.sequence.QueryStart
import org.pitest.sequence.Result.result
import org.pitest.sequence.SequenceMatcher
import org.pitest.sequence.Slot
import org.pitest.sequence.SlotWrite

internal class KotlinCoroutineSuspensionMutationFilter :
    MutatorSpecificInterceptor(listOf(NegateConditionalsMutator.NEGATE_CONDITIONALS)) {
    override fun computeRegions(method: MethodTree): List<Region> {
        if (!method.rawNode().desc.endsWith(COROUTINE_METHOD_SUFFIX)) return emptyList()

        return SUSPENSION_CHECK
            .contextMatches(method.instructions(), Context.start())
            .map { context ->
                val comparison = context.retrieve(MUTATED_INSTRUCTION.read()).orElseThrow()
                Region(comparison, comparison)
            }
    }

    private companion object {
        const val COROUTINE_METHOD_SUFFIX = "Lkotlin/coroutines/Continuation;)Ljava/lang/Object;"
        val MUTATED_INSTRUCTION: Slot<AbstractInsnNode> = Slot.create(AbstractInsnNode::class.java)
        val SUSPENDED_VALUE_VARIABLE: Slot<Int> = Slot.create(Int::class.java)

        val SUSPENSION_CHECK: SequenceMatcher<AbstractInsnNode> =
            QueryStart
                .any(AbstractInsnNode::class.java)
                .then(suspendCall())
                .then(DUP)
                .then(ALOAD.and(aVariableAccess(SUSPENDED_VALUE_VARIABLE.write())))
                .then(IF_ACMPNE.and(store(MUTATED_INSTRUCTION.write())))
                .then(ALOAD.and(variableMatches(SUSPENDED_VALUE_VARIABLE.read())))
                .then(ARETURN)
                .zeroOrMore(QueryStart.match(anyInstruction()))
                .compile(
                    QueryParams
                        .params(AbstractInsnNode::class.java)
                        .withIgnores(notAnInstruction().or(isA(LabelNode::class.java))),
                )

        fun suspendCall(): Match<AbstractInsnNode> =
            Match { context, instruction ->
                result(
                    instruction is MethodInsnNode && instruction.desc.endsWith(COROUTINE_METHOD_SUFFIX),
                    context,
                )
            }

        fun store(slot: SlotWrite<AbstractInsnNode>): Match<AbstractInsnNode> =
            Match { context, instruction -> result(true, context.store(slot, instruction)) }
    }
}
