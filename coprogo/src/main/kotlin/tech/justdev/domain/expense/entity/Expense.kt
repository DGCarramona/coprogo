package tech.justdev.domain.expense.entity

import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.expense.valueobject.ExpenseParticipation
import tech.justdev.domain.expense.valueobject.ExpenseParticipationDecision
import tech.justdev.domain.expense.valueobject.ExpenseParticipationStatus
import tech.justdev.domain.expense.valueobject.ExpenseShare
import tech.justdev.domain.expense.valueobject.ExpenseStatus
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.domain.shared.valueobject.MemberId
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.money.sum
import java.time.Instant

data class Expense(
    val id: ExpenseId,
    val group: GroupId,
    val title: String,
    val createdBy: MemberId,
    val totalAmount: MoneyAmount,
    val createdAt: Instant,
    val participations: Set<ExpenseParticipation>,
) {
    init {
        require(title.isNotBlank()) { "title must not be blank" }
        require(participations.isNotEmpty()) { "participations must not be empty" }
        require(participations.map { it.member }.toSet().size == participations.size) {
            "participations must contain unique members"
        }
        require(participations.map { it.amount }.sum() == totalAmount) {
            "participation amounts must add up to totalAmount"
        }
        require(participations.any { it.member == createdBy && it.amount > MoneyAmount.ZERO }) {
            "creator must participate in the expense"
        }
    }

    val status: ExpenseStatus
        get() = when {
            participations.any { it.status is ExpenseParticipationStatus.Refused } -> ExpenseStatus.INVALIDATED
            participations.all { it.status is ExpenseParticipationStatus.Approved } -> ExpenseStatus.ACCEPTED
            else -> ExpenseStatus.PROPOSED
        }

    fun recordParticipationDecision(
        member: MemberId,
        decision: ExpenseParticipationDecision,
        decidedAt: Instant,
    ): Expense {
        require(status == ExpenseStatus.PROPOSED) { "expense is not awaiting participant decisions" }
        require(member != createdBy) { "creator participation is approved at creation time" }

        val currentParticipation = participations.find { it.member == member }
            ?: throw IllegalArgumentException("member is not part of this expense")

        require(currentParticipation.status is ExpenseParticipationStatus.Pending) {
            "member participation decision was already recorded"
        }

        val updatedParticipation = currentParticipation.copy(
            status = when (decision) {
                ExpenseParticipationDecision.APPROVE -> ExpenseParticipationStatus.Approved(decidedAt)
                ExpenseParticipationDecision.REFUSE -> ExpenseParticipationStatus.Refused(decidedAt)
            },
        )

        return copy(
            participations = participations
                .minus(currentParticipation)
                .plus(updatedParticipation),
        )
    }

    fun acceptedAt(): Instant {
        require(status == ExpenseStatus.ACCEPTED) { "expense must be accepted" }

        return participations
            .mapNotNull { participation -> (participation.status as? ExpenseParticipationStatus.Approved)?.decidedAt }
            .maxOrNull()
            ?: throw IllegalStateException("accepted expense must expose an approval timestamp")
    }

    companion object {
        fun proposeEqualSplit(
            id: ExpenseId,
            group: GroupId,
            title: String,
            createdBy: MemberId,
            totalAmount: MoneyAmount,
            createdAt: Instant,
            participants: Set<MemberId>,
        ): Expense {
            require(participants.isNotEmpty()) { "participantMemberIds must not be empty" }

            val sortedParticipants = participants.sortedBy { member -> member.toPrimitive() }
            val allocatedAmounts = totalAmount.splitEvenly(sortedParticipants.size)

            require(allocatedAmounts.none(MoneyAmount::isZero)) {
                "equal split requires at least 1 cent per participant"
            }

            val shares = sortedParticipants
                .withIndex()
                .map { indexedParticipant ->
                    ExpenseShare(
                        member = indexedParticipant.value,
                        amount = allocatedAmounts[indexedParticipant.index],
                    )
                }
                .toSet()

            return propose(
                id = id,
                group = group,
                title = title,
                createdBy = createdBy,
                totalAmount = totalAmount,
                createdAt = createdAt,
                shares = shares,
            )
        }

        fun propose(
            id: ExpenseId,
            group: GroupId,
            title: String,
            createdBy: MemberId,
            totalAmount: MoneyAmount,
            createdAt: Instant,
            shares: Set<ExpenseShare>,
        ): Expense {
            require(shares.isNotEmpty()) { "shares must not be empty" }
            require(shares.map { it.member }.toSet().size == shares.size) { "shares must contain unique members" }
            require(shares.any { it.member == createdBy && it.amount > MoneyAmount.ZERO }) {
                "creator must participate in the expense"
            }

            val participations = shares.map { share ->
                ExpenseParticipation(
                    member = share.member,
                    amount = share.amount,
                    status = if (share.member == createdBy) {
                        ExpenseParticipationStatus.Approved(createdAt)
                    } else {
                        ExpenseParticipationStatus.Pending
                    },
                )
            }.toSet()

            return Expense(
                id = id,
                group = group,
                title = title,
                createdBy = createdBy,
                totalAmount = totalAmount,
                createdAt = createdAt,
                participations = participations,
            )
        }
    }
}
