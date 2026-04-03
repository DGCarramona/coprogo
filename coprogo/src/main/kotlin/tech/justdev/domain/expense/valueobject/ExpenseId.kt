package tech.justdev.domain.expense.valueobject

@JvmInline
value class ExpenseId(val value: String) {
    init {
        require(value.isNotBlank()) { "expenseId must not be blank" }
    }
}
