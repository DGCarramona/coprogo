package tech.justdev.interfaces.ledger

import io.micronaut.serde.annotation.Serdeable
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.ledger.event.AcceptedExpenseLedgerEvent
import tech.justdev.domain.ledger.event.AcceptedReimbursementLedgerEvent
import tech.justdev.domain.ledger.event.CashPoolIncomeLedgerEvent
import tech.justdev.domain.ledger.event.CashPoolWithdrawalLedgerEvent
import tech.justdev.domain.ledger.event.LedgerEvent
import tech.justdev.domain.ledger.projection.MemberBalanceImpact
import tech.justdev.domain.ledger.projection.MemberBalanceImpactType
import tech.justdev.domain.shared.valueobject.GroupId
import java.time.Instant
import java.util.UUID

object MemberBalanceImpactPresenter {
    operator fun invoke(
        group: GroupId,
        member: MemberEmail,
        impacts: List<MemberBalanceImpact>,
    ): MemberBalanceImpactHistoryResponse =
        MemberBalanceImpactHistoryResponse(
            group = group.toPrimitive(),
            member = member.toPrimitive(),
            impacts = impacts.map(::present),
        )

    private fun present(impact: MemberBalanceImpact): MemberBalanceImpactResponse =
        operationFrom(impact.event).let {
            MemberBalanceImpactResponse(
                type = it.type,
                effect =
                    when (impact.effect) {
                        MemberBalanceImpactType.DEBT_REDUCTION -> MemberBalanceImpactTypeResponse.DEBT_REDUCTION
                        MemberBalanceImpactType.DEBT_INCREASE -> MemberBalanceImpactTypeResponse.DEBT_INCREASE
                        MemberBalanceImpactType.CREDIT_REDUCTION -> MemberBalanceImpactTypeResponse.CREDIT_REDUCTION
                    },
                operationId = it.id,
                amountInCents = impact.amount.inCents(),
                occurredAt = impact.event.occurredAt,
            )
        }

    private fun operationFrom(event: LedgerEvent): BalanceImpactOperation =
        when (event) {
            is AcceptedExpenseLedgerEvent -> {
                BalanceImpactOperation(
                    type = MemberBalanceImpactOperationTypeResponse.COMPENSATING_EXPENSE,
                    id = event.expense.toPrimitive(),
                )
            }

            is AcceptedReimbursementLedgerEvent -> {
                BalanceImpactOperation(
                    type = MemberBalanceImpactOperationTypeResponse.DIRECT_REIMBURSEMENT,
                    id = event.reimbursement.toPrimitive(),
                )
            }

            is CashPoolWithdrawalLedgerEvent -> {
                BalanceImpactOperation(
                    type = MemberBalanceImpactOperationTypeResponse.CASH_POOL_WITHDRAWAL,
                    id = event.id.toPrimitive(),
                )
            }

            is CashPoolIncomeLedgerEvent -> {
                error("cash-pool income cannot reduce member debt")
            }
        }
}

@Serdeable
data class MemberBalanceImpactHistoryResponse(
    val group: UUID,
    val member: String,
    val impacts: List<MemberBalanceImpactResponse>,
)

@Serdeable
data class MemberBalanceImpactResponse(
    val type: MemberBalanceImpactOperationTypeResponse,
    val effect: MemberBalanceImpactTypeResponse,
    val operationId: UUID,
    val amountInCents: Long,
    val occurredAt: Instant,
)

@Serdeable
enum class MemberBalanceImpactOperationTypeResponse {
    DIRECT_REIMBURSEMENT,
    COMPENSATING_EXPENSE,
    CASH_POOL_WITHDRAWAL,
}

@Serdeable
enum class MemberBalanceImpactTypeResponse {
    DEBT_REDUCTION,
    DEBT_INCREASE,
    CREDIT_REDUCTION,
}

private data class BalanceImpactOperation(
    val type: MemberBalanceImpactOperationTypeResponse,
    val id: UUID,
)
