package tech.justdev.domain.expense.valueobject

import java.util.UUID

@JvmInline
value class ExpenseId(private val value: UUID) {
    fun toPrimitive(): UUID = value
}
