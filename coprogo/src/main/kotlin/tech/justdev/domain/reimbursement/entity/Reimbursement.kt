package tech.justdev.domain.reimbursement.entity

import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.reimbursement.valueobject.ReimbursementId
import tech.justdev.domain.reimbursement.valueobject.ReimbursementStatus
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import java.time.Instant

class Reimbursement private constructor(
    val id: ReimbursementId,
    val group: GroupId,
    val paidBy: MemberEmail,
    val receivedBy: MemberEmail,
    val amount: MoneyAmount,
    val reimbursedAt: Instant,
    val declaredBy: MemberEmail,
    val declaredAt: Instant,
    val status: ReimbursementStatus,
) {
    companion object {
        fun recordDirect(
            id: ReimbursementId,
            group: GroupId,
            paidBy: MemberEmail,
            receivedBy: MemberEmail,
            amount: MoneyAmount,
            reimbursedAt: Instant,
            declaredBy: MemberEmail,
            declaredAt: Instant,
        ): Reimbursement {
            require(declaredBy == receivedBy) {
                "direct reimbursement must be recorded by its receiver"
            }

            return restore(
                id = id,
                group = group,
                paidBy = paidBy,
                receivedBy = receivedBy,
                amount = amount,
                reimbursedAt = reimbursedAt,
                declaredBy = declaredBy,
                declaredAt = declaredAt,
                status = ReimbursementStatus.Accepted(declaredAt),
            )
        }

        fun restore(
            id: ReimbursementId,
            group: GroupId,
            paidBy: MemberEmail,
            receivedBy: MemberEmail,
            amount: MoneyAmount,
            reimbursedAt: Instant,
            declaredBy: MemberEmail,
            declaredAt: Instant,
            status: ReimbursementStatus,
        ): Reimbursement {
            require(paidBy != receivedBy) {
                "reimbursement payer and receiver must be different"
            }
            require(declaredBy == paidBy || declaredBy == receivedBy) {
                "reimbursement must be declared by its payer or receiver"
            }
            require(amount > MoneyAmount.ZERO) {
                "reimbursement amount must be strictly positive"
            }
            require(reimbursedAt <= declaredAt) {
                "reimbursement must not occur after its declaration"
            }
            when (status) {
                is ReimbursementStatus.Accepted -> {
                    require(status.acceptedAt >= declaredAt) {
                        "reimbursement acceptance must not precede its declaration"
                    }
                }
            }

            return Reimbursement(
                id = id,
                group = group,
                paidBy = paidBy,
                receivedBy = receivedBy,
                amount = amount,
                reimbursedAt = reimbursedAt,
                declaredBy = declaredBy,
                declaredAt = declaredAt,
                status = status,
            )
        }
    }
}
