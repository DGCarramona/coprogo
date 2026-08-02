package tech.justdev.application.expense

import jakarta.inject.Singleton
import tech.justdev.domain.expense.valueobject.ExpenseId
import java.util.UUID

fun interface ExpenseIdGenerator {
    fun next(): ExpenseId
}

@Singleton
class RandomExpenseIdGenerator : ExpenseIdGenerator {
    override fun next(): ExpenseId = ExpenseId(UUID.randomUUID())
}
