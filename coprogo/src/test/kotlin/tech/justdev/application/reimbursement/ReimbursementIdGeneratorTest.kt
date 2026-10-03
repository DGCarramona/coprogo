package tech.justdev.application.reimbursement

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import tech.justdev.testsupport.NoDbMicronautTest

@NoDbMicronautTest
class ReimbursementIdGeneratorTest {
    @Inject
    lateinit var reimbursementIdGenerator: ReimbursementIdGenerator

    @Test
    fun `should provide distinct usable reimbursement ids`() {
        val first = reimbursementIdGenerator.next()
        val second = reimbursementIdGenerator.next()

        assertNotNull(first.toPrimitive())
        assertNotNull(second.toPrimitive())
        assertNotEquals(first, second)
    }
}
