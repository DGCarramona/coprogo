package tech.justdev.pitest

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pitest.mutationtest.MutationResultListenerFactory
import org.pitest.mutationtest.build.MutationInterceptorFactory
import java.util.ServiceLoader

class PitestPluginDiscoveryTest {
    @Test
    fun `discovers the filter and enabled candidate listener through service loader`() {
        val interceptorFactories = ServiceLoader.load(MutationInterceptorFactory::class.java).map { factory -> factory::class.java }
        val listenerFactories = ServiceLoader.load(MutationResultListenerFactory::class.java).toList()
        val candidateFactory = listenerFactories.single { factory -> factory is EquivalentMutationCandidateListenerFactory }

        assertTrue(CoprogoEquivalentKotlinMutationFilterFactory::class.java in interceptorFactories)
        assertTrue(candidateFactory.provides().isOnByDefault)
    }
}
