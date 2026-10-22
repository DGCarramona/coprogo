package tech.justdev.interfaces.ledger

import io.micronaut.serde.annotation.Serdeable
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.ledger.event.AcceptedExpenseLedgerEvent
import tech.justdev.domain.ledger.event.AcceptedReimbursementLedgerEvent
import tech.justdev.domain.ledger.event.CashPoolIncomeLedgerEvent
import tech.justdev.domain.ledger.event.CashPoolWithdrawalLedgerEvent
import tech.justdev.domain.ledger.event.LedgerEvent
import tech.justdev.domain.ledger.projection.MemberDebtReduction
import tech.justdev.domain.shared.valueobject.GroupId
import java.time.Instant
import java.util.UUID

object MemberDebtReductionPresenter {
    operator fun invoke(
        group: GroupId,
        member: MemberEmail,
        reductions: List<MemberDebtReduction>,
    ): MemberDebtReductionHistoryResponse =
        MemberDebtReductionHistoryResponse(
            group = group.toPrimitive(),
            member = member.toPrimitive(),
            reductions = reductions.map(::present),
        )

    private fun present(reduction: MemberDebtReduction): MemberDebtReductionResponse =
        operationFrom(reduction.event).let {
            MemberDebtReductionResponse(
                type = it.type,
                operationId = it.id,
                amountInCents = reduction.amount.inCents(),
                occurredAt = reduction.event.occurredAt,
            )
        }

    private fun operationFrom(event: LedgerEvent): DebtReductionOperation =
        when (event) {
            is AcceptedExpenseLedgerEvent -> {
                DebtReductionOperation(
                    type = MemberDebtReductionTypeResponse.COMPENSATING_EXPENSE,
                    id = event.expense.toPrimitive(),
                )
            }

            is AcceptedReimbursementLedgerEvent -> {
                DebtReductionOperation(
                    type = MemberDebtReductionTypeResponse.DIRECT_REIMBURSEMENT,
                    id = event.reimbursement.toPrimitive(),
                )
            }

            is CashPoolWithdrawalLedgerEvent -> {
                DebtReductionOperation(
                    type = MemberDebtReductionTypeResponse.CASH_POOL_WITHDRAWAL,
                    id = event.id.toPrimitive(),
                )
            }

            is CashPoolIncomeLedgerEvent -> {
                error("cash-pool income cannot reduce member debt")
            }
        }
}

@Serdeable
data class MemberDebtReductionHistoryResponse(
    val group: UUID,
    val member: String,
    val reductions: List<MemberDebtReductionResponse>,
)

@Serdeable
data class MemberDebtReductionResponse(
    val type: MemberDebtReductionTypeResponse,
    val operationId: UUID,
    val amountInCents: Long,
    val occurredAt: Instant,
)

@Serdeable
enum class MemberDebtReductionTypeResponse {
    DIRECT_REIMBURSEMENT,
    COMPENSATING_EXPENSE,
    CASH_POOL_WITHDRAWAL,
}

private data class DebtReductionOperation(
    val type: MemberDebtReductionTypeResponse,
    val id: UUID,
)
