package tech.justdev.domain.group.valueobject

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MemberEmailTest {
    @Test
    fun `of should normalize email to lowercase and trim surrounding whitespace`() {
        val email = MemberEmail.of("  Member@Example.com ")

        assertEquals("member@example.com", email.toPrimitive())
    }

    @Test
    fun `of should fail when email is blank`() {
        assertThrows<IllegalArgumentException> {
            MemberEmail.of("   ")
        }
    }

    @Test
    fun `of should fail when email does not contain a single at sign`() {
        assertThrows<IllegalArgumentException> {
            MemberEmail.of("member.example.com")
        }
    }
}
