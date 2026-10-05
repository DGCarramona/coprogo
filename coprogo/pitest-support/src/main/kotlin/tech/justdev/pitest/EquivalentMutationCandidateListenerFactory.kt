package tech.justdev.pitest

import org.pitest.mutationtest.ListenerArguments
import org.pitest.mutationtest.MutationResultListener
import org.pitest.mutationtest.MutationResultListenerFactory
import org.pitest.plugin.Feature
import java.util.Properties

class EquivalentMutationCandidateListenerFactory : MutationResultListenerFactory {
    override fun getListener(
        properties: Properties,
        arguments: ListenerArguments,
    ): MutationResultListener = EquivalentMutationCandidateListener(System.out)

    override fun name(): String = "COPROGO_EQUIVALENT_CANDIDATES"

    override fun description(): String = "Reports surviving mutations as human-readable review blocks"

    override fun provides(): Feature =
        Feature
            .named("FCOPROGO_EQUIVALENT_CANDIDATES")
            .withDescription(description())
            .withOnByDefault(true)
}
