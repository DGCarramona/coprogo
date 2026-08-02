package tech.justdev.application.ledger

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import tech.justdev.testsupport.NoDbMicronautTest

@NoDbMicronautTest
class LedgerEventIdGeneratorTest {
    @Inject
    lateinit var ledgerEventIdGenerator: LedgerEventIdGenerator

    @Test
    fun `should provide distinct usable ledger event ids`() {
        val first = ledgerEventIdGenerator.next()
        val second = ledgerEventIdGenerator.next()

        assertNotNull(first.toPrimitive())
        assertNotNull(second.toPrimitive())
        assertNotEquals(first, second)
    }
}
