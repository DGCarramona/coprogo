package tech.justdev.pitest

import org.pitest.mutationtest.build.InterceptorParameters
import org.pitest.mutationtest.build.MutationInterceptor
import org.pitest.mutationtest.build.MutationInterceptorFactory
import org.pitest.plugin.Feature

class KotlinCoroutineSuspensionMutationFilterFactory : MutationInterceptorFactory {
    override fun description(): String = "Filters condition mutations in Kotlin coroutine suspension checks"

    override fun createInterceptor(parameters: InterceptorParameters): MutationInterceptor = KotlinCoroutineSuspensionMutationFilter()

    override fun provides(): Feature =
        Feature
            .named("FCOPROGO_KOTLIN_COROUTINES")
            .withDescription(description())
            .withOnByDefault(true)
}
