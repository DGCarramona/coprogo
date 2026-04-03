package tech.justdev.testsupport

import io.micronaut.test.support.TestPropertyProvider
import io.micronaut.test.support.TestPropertyProviderFactory
import org.junit.platform.commons.support.AnnotationSupport

class PostgresTestPropertyProviderFactory : TestPropertyProviderFactory {
    override fun create(
        availableProperties: Map<String, Any>,
        testClass: Class<*>,
    ): TestPropertyProvider =
        TestPropertyProvider {
            if (usesPostgresTestDatabase(testClass)) {
                PostgresTestDatabase.properties()
            } else {
                emptyMap()
            }
        }

    private fun usesPostgresTestDatabase(testClass: Class<*>): Boolean =
        AnnotationSupport.findAnnotation(testClass, UsesPostgresTestDatabase::class.java).isPresent
}
