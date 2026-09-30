package tech.justdev.pitest

import org.pitest.bytecode.analysis.ClassTree
import org.pitest.mutationtest.build.InterceptorType
import org.pitest.mutationtest.build.MutationInterceptor
import org.pitest.mutationtest.engine.Mutater
import org.pitest.mutationtest.engine.MutationDetails

internal class CoprogoEquivalentKotlinMutationFilter(
    private val whitelist: EquivalentMutationWhitelist,
) : MutationInterceptor {
    override fun type(): InterceptorType = InterceptorType.FILTER

    override fun begin(clazz: ClassTree) = Unit

    override fun intercept(
        mutations: Collection<MutationDetails>,
        mutater: Mutater?,
    ): Collection<MutationDetails> =
        mutations.filterNot { mutation ->
            isEquivalent(
                className = mutation.className.asJavaName(),
                method = mutation.method,
                mutator = mutation.mutator,
                instructionIndex = mutation.firstIndex,
            )
        }

    override fun end() = Unit

    fun isEquivalent(
        className: String,
        method: String,
        mutator: String,
        instructionIndex: Int,
    ): Boolean =
        MutationSignature(
            className = className,
            method = method,
            mutator = mutator,
            instructionIndex = instructionIndex,
        ) in whitelist
}
