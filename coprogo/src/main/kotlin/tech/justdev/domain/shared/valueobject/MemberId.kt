package tech.justdev.domain.shared.valueobject

import java.util.UUID

@JvmInline
value class MemberId(private val value: UUID) {
    fun toPrimitive(): UUID = value
}
