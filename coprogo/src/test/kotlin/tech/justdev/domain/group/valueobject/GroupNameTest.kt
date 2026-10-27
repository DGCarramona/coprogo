package tech.justdev.domain.group.valueobject

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class GroupNameTest {
    @Nested
    inner class Of {
        @Test
        fun `normalizes surrounding whitespace and preserves the name`() {
            assertEquals("Appartement familial", GroupName.of("  Appartement familial  ").toPrimitive())
        }

        @Test
        fun `accepts exactly one hundred characters`() {
            assertEquals("a".repeat(100), GroupName.of("a".repeat(100)).toPrimitive())
        }

        @ParameterizedTest
        @ValueSource(strings = ["", " ", "\t\n"])
        fun `rejects a blank name`(name: String) {
            assertThrows<IllegalArgumentException> { GroupName.of(name) }
        }

        @Test
        fun `rejects more than one hundred characters`() {
            assertThrows<IllegalArgumentException> { GroupName.of("a".repeat(101)) }
        }
    }
}
