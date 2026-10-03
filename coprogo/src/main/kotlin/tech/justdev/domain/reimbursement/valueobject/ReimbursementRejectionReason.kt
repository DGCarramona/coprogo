package tech.justdev.domain.reimbursement.valueobject

@JvmInline
value class ReimbursementRejectionReason private constructor(
    private val value: String,
) {
    fun toPrimitive(): String = value

    companion object {
        fun of(value: String): ReimbursementRejectionReason {
            require(value.isNotBlank()) { "reimbursement rejection reason must not be blank" }

            return ReimbursementRejectionReason(value)
        }
    }
}
