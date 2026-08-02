package tech.justdev.application.expense

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import tech.justdev.testsupport.NoDbMicronautTest

@NoDbMicronautTest
class ExpenseIdGeneratorTest {
    @Inject
    lateinit var expenseIdGenerator: ExpenseIdGenerator

    @Test
    fun `should provide distinct usable expense ids`() {
        val first = expenseIdGenerator.next()
        val second = expenseIdGenerator.next()

        assertNotNull(first.toPrimitive())
        assertNotNull(second.toPrimitive())
        assertNotEquals(first, second)
    }
}
