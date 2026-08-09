package tech.justdev.domain.expense.valueobject

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RefusalReasonTest {
    @Test
    fun `of should preserve free text`() {
        val text = "  This expense was not agreed. Please check the invoice details.  "

        assertEquals(text, RefusalReason.of(text).toPrimitive())
    }

    @Test
    fun `of should reject a blank reason`() {
        val error = assertThrows<IllegalArgumentException> { RefusalReason.of("   ") }

        assertEquals("refusal reason must not be blank", error.message)
    }
}
