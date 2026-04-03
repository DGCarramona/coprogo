package tech.justdev

import io.micronaut.runtime.EmbeddedApplication
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import tech.justdev.testsupport.PostgresMicronautTest

@PostgresMicronautTest
class CoprogoTest {

    @Inject
    lateinit var application: EmbeddedApplication<*>

    @Test
    fun testItWorks() {
        Assertions.assertTrue(application.isRunning)
    }

}
