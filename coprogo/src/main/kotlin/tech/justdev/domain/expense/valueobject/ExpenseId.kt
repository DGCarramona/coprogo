package tech.justdev.domain.expense.valueobject

import java.util.UUID

@JvmInline
value class ExpenseId(val value: UUID) {
    override fun toString(): String {
        return value.toString()
    }
}
