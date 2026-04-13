package tech.justdev.testsupport

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import java.lang.annotation.Inherited

/**
 * Standard entry point for backend Micronaut integration tests that need the application context
 * but do not exercise a real database.
 *
 * This enables the dedicated `integration-no-db` Micronaut environment with the shared
 * datasource/Flyway overrides for no-database tests.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Inherited
@MicronautTest(
    transactional = false,
    environments = ["integration-no-db"],
)
annotation class NoDbMicronautTest
