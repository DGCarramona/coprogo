package tech.justdev.testsupport

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import java.lang.annotation.Inherited

/**
 * Standard entry point for backend Micronaut tests that need PostgreSQL through Micronaut Test Resources.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Inherited
@MicronautTest(transactional = false)
annotation class PostgresMicronautTest
