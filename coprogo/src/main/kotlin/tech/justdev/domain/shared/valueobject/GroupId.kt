package tech.justdev.domain.shared.valueobject

import java.util.UUID

@JvmInline
value class GroupId(val value: UUID) {
    override fun toString(): String {
        return value.toString()
    }
}
