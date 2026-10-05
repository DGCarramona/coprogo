package tech.justdev.domain.reimbursement.valueobject

import java.util.UUID

@JvmInline
value class ReimbursementId(
    private val value: UUID,
) {
    fun toPrimitive(): UUID = value
}
