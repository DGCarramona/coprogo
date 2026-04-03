package tech.justdev.application.expense

import tech.justdev.domain.expense.valueobject.ExpenseId
import java.util.UUID

fun interface ExpenseIdGenerator {
    fun next(): ExpenseId
}

object RandomExpenseIdGenerator : ExpenseIdGenerator {
    override fun next(): ExpenseId = ExpenseId(UUID.randomUUID())
}
