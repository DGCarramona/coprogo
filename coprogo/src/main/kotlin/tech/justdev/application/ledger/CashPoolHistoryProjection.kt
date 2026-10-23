package tech.justdev.application.ledger

import tech.justdev.domain.shared.valueobject.GroupId
import java.time.Instant
import java.util.UUID

interface CashPoolHistoryProjection {
    suspend fun findByGroup(group: GroupId): List<CashPoolOperationSnapshot>
}

sealed interface CashPoolOperationSnapshot {
    val operation: UUID
    val occurredAt: Instant
}

data class CashPoolIncomeSnapshot(
    override val operation: UUID,
    override val occurredAt: Instant,
    val amountInCents: Long,
    val allocations: List<CashPoolIncomeAllocationSnapshot>,
) : CashPoolOperationSnapshot

data class CashPoolIncomeAllocationSnapshot(
    val member: String,
    val amountInCents: Long,
)

data class CashPoolWithdrawalSnapshot(
    override val operation: UUID,
    override val occurredAt: Instant,
    val withdrawnBy: String,
    val amountInCents: Long,
    val ownRevenueShareConsumedInCents: Long,
) : CashPoolOperationSnapshot
