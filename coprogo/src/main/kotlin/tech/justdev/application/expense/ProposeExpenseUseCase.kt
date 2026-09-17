package tech.justdev.application.expense

import jakarta.inject.Singleton
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.domain.document.entity.DocumentUploadIntent
import tech.justdev.domain.document.repository.DocumentUploadIntentRepository
import tech.justdev.domain.document.valueobject.DocumentUploadIntentId
import tech.justdev.domain.expense.entity.CumulativeExpenseTier
import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.expense.valueobject.ExpenseShare
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import java.time.Instant

sealed interface ExpenseAllocationCommand

data class EqualSplitExpenseAllocationCommand(
    val participants: Set<MemberEmail>,
) : ExpenseAllocationCommand

data class EqualSplitWithCapsExpenseAllocationCommand(
    val participants: Set<MemberEmail>,
    val capsInCentsByMember: Map<MemberEmail, Long>,
) : ExpenseAllocationCommand

data class CumulativeTiersExpenseAllocationCommand(
    val tiers: List<CumulativeExpenseTierCommand>,
) : ExpenseAllocationCommand

data class CumulativeExpenseTierCommand(
    val upToAmountInCents: Long,
    val participants: Set<MemberEmail>,
)

data class CustomExpenseAllocationCommand(
    val participations: Set<CustomExpenseParticipationCommand>,
) : ExpenseAllocationCommand

data class CustomExpenseParticipationCommand(
    val member: MemberEmail,
    val amountInCents: Long,
)

data class ProposeExpenseCommand(
    val group: GroupId,
    val title: String,
    val createdBy: MemberEmail,
    val totalAmountInCents: Long,
    val createdAt: Instant,
    val allocation: ExpenseAllocationCommand,
    val supportingDocumentUploadIntents: Set<DocumentUploadIntentId> = emptySet(),
)

class SupportingDocumentUploadIntentUnavailableException :
    RuntimeException(
        "supporting document upload intent is unavailable",
    )

interface ProposeExpenseUseCase {
    suspend operator fun invoke(command: ProposeExpenseCommand)
}

@Singleton
class ProposeExpenseUseCaseImpl(
    private val groupAccessPolicy: GroupAccessPolicy,
    private val expenseIdGenerator: ExpenseIdGenerator,
    private val documentUploadIntentRepository: DocumentUploadIntentRepository,
    private val expenseProposalPersistence: ExpenseProposalPersistence,
) : ProposeExpenseUseCase {
    private suspend fun consumeUploadIntents(command: ProposeExpenseCommand) =
        documentUploadIntentRepository
            .findReadyByIdsAndGroupAndUploader(
                ids = command.supportingDocumentUploadIntents,
                group = command.group,
                uploader = command.createdBy,
            ).also {
                if (it.map(DocumentUploadIntent::id).toSet() != command.supportingDocumentUploadIntents.toSet()) {
                    throw SupportingDocumentUploadIntentUnavailableException()
                }
            }.map { intent ->
                try {
                    intent.consume(command.createdAt)
                } catch (_: IllegalArgumentException) {
                    throw SupportingDocumentUploadIntentUnavailableException()
                }
            }

    override suspend operator fun invoke(command: ProposeExpenseCommand) {
        val group = groupAccessPolicy.requireMember(command.group, command.createdBy)
        val consumedUploadIntents = this.consumeUploadIntents(command)

        val nonMember =
            when (val allocation = command.allocation) {
                is EqualSplitExpenseAllocationCommand -> allocation.participants
                is EqualSplitWithCapsExpenseAllocationCommand -> allocation.participants + allocation.capsInCentsByMember.keys
                is CumulativeTiersExpenseAllocationCommand -> allocation.tiers.flatMap { tier -> tier.participants }.toSet()
                is CustomExpenseAllocationCommand -> allocation.participations.map { participation -> participation.member }.toSet()
            }.let {
                it
                    .filterNot(group::contains)
                    .minByOrNull { member -> member.toPrimitive() }
            }
        if (nonMember != null) {
            throw IllegalArgumentException(
                "expense participant ${nonMember.toPrimitive()} is not part of group ${command.group.toPrimitive()}",
            )
        }

        expenseProposalPersistence.persist(
            command
                .toExpense(expenseIdGenerator.next())
                .attachSupportingDocuments(consumedUploadIntents),
            consumedUploadIntents,
        )
    }
}

private fun ProposeExpenseCommand.toExpense(expenseId: ExpenseId): Expense =
    when (val allocation = allocation) {
        is EqualSplitExpenseAllocationCommand -> {
            Expense.proposeEqualSplit(
                id = expenseId,
                group = group,
                title = title,
                createdBy = createdBy,
                totalAmount = MoneyAmount.ofCents(totalAmountInCents),
                createdAt = createdAt,
                participants = allocation.participants,
            )
        }

        is EqualSplitWithCapsExpenseAllocationCommand -> {
            Expense.proposeEqualSplitWithCaps(
                id = expenseId,
                group = group,
                title = title,
                createdBy = createdBy,
                totalAmount = MoneyAmount.ofCents(totalAmountInCents),
                createdAt = createdAt,
                participants = allocation.participants,
                capsByMember = allocation.capsInCentsByMember.mapValues { (_, amountInCents) -> MoneyAmount.ofCents(amountInCents) },
            )
        }

        is CumulativeTiersExpenseAllocationCommand -> {
            Expense.proposeCumulativeTiers(
                id = expenseId,
                group = group,
                title = title,
                createdBy = createdBy,
                totalAmount = MoneyAmount.ofCents(totalAmountInCents),
                createdAt = createdAt,
                tiers =
                    allocation.tiers.map { tier ->
                        CumulativeExpenseTier(
                            upTo = MoneyAmount.ofCents(tier.upToAmountInCents),
                            participants = tier.participants,
                        )
                    },
            )
        }

        is CustomExpenseAllocationCommand -> {
            Expense.propose(
                id = expenseId,
                group = group,
                title = title,
                createdBy = createdBy,
                totalAmount = MoneyAmount.ofCents(totalAmountInCents),
                createdAt = createdAt,
                shares =
                    allocation.participations
                        .map { participation ->
                            ExpenseShare(
                                member = participation.member,
                                amount = MoneyAmount.ofCents(participation.amountInCents),
                            )
                        }.toSet(),
            )
        }
    }
