package tech.justdev.domain.expense.valueobject

@JvmInline
value class RefusalReason private constructor(
    private val value: String,
) {
    fun toPrimitive(): String = value

    companion object {
        fun of(value: String): RefusalReason {
            require(value.isNotBlank()) { "refusal reason must not be blank" }
            return RefusalReason(value)
        }
    }
}
