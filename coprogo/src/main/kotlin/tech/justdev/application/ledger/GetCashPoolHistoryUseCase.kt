package tech.justdev.application.ledger

import jakarta.inject.Singleton
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId
import java.util.UUID

data class GetCashPoolHistoryQuery(
    val group: GroupId,
    val requestedBy: MemberEmail,
)

data class CashPoolHistorySnapshot(
    val group: UUID,
    val operations: List<CashPoolOperationSnapshot>,
)

interface GetCashPoolHistoryUseCase {
    suspend operator fun invoke(query: GetCashPoolHistoryQuery): CashPoolHistorySnapshot
}

@Singleton
class GetCashPoolHistoryUseCaseImpl(
    private val groupAccessPolicy: GroupAccessPolicy,
    private val cashPoolHistoryProjection: CashPoolHistoryProjection,
) : GetCashPoolHistoryUseCase {
    override suspend fun invoke(query: GetCashPoolHistoryQuery): CashPoolHistorySnapshot {
        groupAccessPolicy.requireMember(query.group, query.requestedBy)

        return CashPoolHistorySnapshot(
            group = query.group.toPrimitive(),
            operations =
                cashPoolHistoryProjection
                    .findByGroup(query.group)
                    .sortedWith(
                        compareByDescending<CashPoolOperationSnapshot> { operation -> operation.occurredAt }
                            .thenByDescending { operation -> operation.operation },
                    ),
        )
    }
}
