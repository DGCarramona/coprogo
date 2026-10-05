package tech.justdev.domain.reimbursement.valueobject

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ReimbursementRejectionReasonTest {
    @Nested
    inner class Of {
        @Test
        fun `should retain a non-blank rejection reason`() {
            val reason = ReimbursementRejectionReason.of("The document does not prove the payment")

            assertEquals("The document does not prove the payment", reason.toPrimitive())
        }

        @Test
        fun `should reject a blank rejection reason`() {
            val error = assertThrows<IllegalArgumentException> { ReimbursementRejectionReason.of("  ") }

            assertEquals("reimbursement rejection reason must not be blank", error.message)
        }
    }
}
