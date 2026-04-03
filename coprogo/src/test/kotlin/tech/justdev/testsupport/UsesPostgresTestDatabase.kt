package tech.justdev.testsupport

import java.lang.annotation.Inherited

/**
 * Marker for Micronaut tests that need the shared PostgreSQL Testcontainers database
 * but cannot use [PostgresMicronautTest] directly.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Inherited
annotation class UsesPostgresTestDatabase
