package tech.justdev.application.reimbursement

import jakarta.inject.Singleton
import tech.justdev.domain.reimbursement.valueobject.ReimbursementId
import java.util.UUID

fun interface ReimbursementIdGenerator {
    fun next(): ReimbursementId
}

@Singleton
class RandomReimbursementIdGenerator : ReimbursementIdGenerator {
    override fun next(): ReimbursementId = ReimbursementId(UUID.randomUUID())
}
