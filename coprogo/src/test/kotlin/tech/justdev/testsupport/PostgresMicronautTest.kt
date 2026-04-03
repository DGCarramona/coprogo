package tech.justdev.testsupport

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import java.lang.annotation.Inherited

/**
 * Standard entry point for backend Micronaut tests that need a real PostgreSQL database.
 *
 * Prefer this annotation over wiring Testcontainers manually in each test class.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Inherited
@MicronautTest
@UsesPostgresTestDatabase
annotation class PostgresMicronautTest
