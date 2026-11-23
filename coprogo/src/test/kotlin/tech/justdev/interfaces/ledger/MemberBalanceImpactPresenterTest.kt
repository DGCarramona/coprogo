package tech.justdev.interfaces.ledger

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.ledger.effect.MemberBalanceTransfer
import tech.justdev.domain.ledger.event.AcceptedExpenseLedgerEvent
import tech.justdev.domain.ledger.event.AcceptedReimbursementLedgerEvent
import tech.justdev.domain.ledger.event.CashPoolWithdrawalLedgerEvent
import tech.justdev.domain.ledger.projection.MemberBalanceImpact
import tech.justdev.domain.ledger.projection.MemberBalanceImpactType
import tech.justdev.domain.reimbursement.valueobject.ReimbursementId
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.testsupport.acceptedExpenseLedgerEventId
import tech.justdev.testsupport.expenseId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.ledgerEventId
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.testUuid
import java.time.Instant

class MemberBalanceImpactPresenterTest {
    @Nested
    inner class Present {
        @Test
        fun `should map every debt impact source without changing history order`() {
            val group = groupId("balance-impact-presenter")
            val member = memberEmail("alice")
            val expense = compensatingExpense(group, member)
            val reimbursement = reimbursement(group, member)
            val withdrawal = withdrawal(group, member)

            val response =
                MemberBalanceImpactPresenter(
                    group = group,
                    member = member,
                    impacts =
                        listOf(
                            MemberBalanceImpact(withdrawal, MoneyAmount.ofCents(10), MemberBalanceImpactType.CREDIT_REDUCTION),
                            MemberBalanceImpact(withdrawal, MoneyAmount.ofCents(10), MemberBalanceImpactType.DEBT_INCREASE),
                            MemberBalanceImpact(reimbursement, MoneyAmount.ofCents(30)),
                            MemberBalanceImpact(expense, MoneyAmount.ofCents(50)),
                        ),
                )

            assertEquals(
                MemberBalanceImpactHistoryResponse(
                    group = group.toPrimitive(),
                    member = member.toPrimitive(),
                    impacts =
                        listOf(
                            MemberBalanceImpactResponse(
                                type = MemberBalanceImpactOperationTypeResponse.CASH_POOL_WITHDRAWAL,
                                effect = MemberBalanceImpactTypeResponse.CREDIT_REDUCTION,
                                operationId = withdrawal.id.toPrimitive(),
                                amountInCents = 10,
                                occurredAt = withdrawal.occurredAt,
                            ),
                            MemberBalanceImpactResponse(
                                type = MemberBalanceImpactOperationTypeResponse.CASH_POOL_WITHDRAWAL,
                                effect = MemberBalanceImpactTypeResponse.DEBT_INCREASE,
                                operationId = withdrawal.id.toPrimitive(),
                                amountInCents = 10,
                                occurredAt = withdrawal.occurredAt,
                            ),
                            MemberBalanceImpactResponse(
                                type = MemberBalanceImpactOperationTypeResponse.DIRECT_REIMBURSEMENT,
                                effect = MemberBalanceImpactTypeResponse.DEBT_REDUCTION,
                                operationId = reimbursement.reimbursement.toPrimitive(),
                                amountInCents = 30,
                                occurredAt = reimbursement.occurredAt,
                            ),
                            MemberBalanceImpactResponse(
                                type = MemberBalanceImpactOperationTypeResponse.COMPENSATING_EXPENSE,
                                effect = MemberBalanceImpactTypeResponse.DEBT_REDUCTION,
                                operationId = expense.expense.toPrimitive(),
                                amountInCents = 50,
                                occurredAt = expense.occurredAt,
                            ),
                        ),
                ),
                response,
            )
        }
    }

    private fun compensatingExpense(
        group: GroupId,
        member: MemberEmail,
    ): AcceptedExpenseLedgerEvent =
        AcceptedExpenseLedgerEvent(
            id = acceptedExpenseLedgerEventId("compensating-expense"),
            group = group,
            expense = expenseId("compensating-expense"),
            paidBy = member,
            occurredAt = Instant.parse("2026-04-03T10:00:00Z"),
            transfers =
                setOf(
                    MemberBalanceTransfer(
                        fromMember = memberEmail("bob"),
                        toMember = member,
                        amount = MoneyAmount.ofCents(50),
                    ),
                ),
        )

    private fun reimbursement(
        group: GroupId,
        member: MemberEmail,
    ): AcceptedReimbursementLedgerEvent =
        AcceptedReimbursementLedgerEvent(
            id = ledgerEventId("reimbursement"),
            group = group,
            reimbursement = ReimbursementId(testUuid("reimbursement")),
            occurredAt = Instant.parse("2026-04-03T11:00:00Z"),
            balanceTransfer =
                MemberBalanceTransfer(
                    fromMember = memberEmail("bob"),
                    toMember = member,
                    amount = MoneyAmount.ofCents(30),
                ),
        )

    private fun withdrawal(
        group: GroupId,
        member: MemberEmail,
    ): CashPoolWithdrawalLedgerEvent =
        CashPoolWithdrawalLedgerEvent(
            id = ledgerEventId("withdrawal"),
            group = group,
            withdrawnBy = memberEmail("bob"),
            withdrawnAmount = MoneyAmount.ofCents(20),
            ownRevenueShareConsumed = MoneyAmount.ZERO,
            balanceTransfers =
                setOf(
                    MemberBalanceTransfer(
                        fromMember = memberEmail("bob"),
                        toMember = member,
                        amount = MoneyAmount.ofCents(20),
                    ),
                ),
            occurredAt = Instant.parse("2026-04-03T12:00:00Z"),
        )
}
