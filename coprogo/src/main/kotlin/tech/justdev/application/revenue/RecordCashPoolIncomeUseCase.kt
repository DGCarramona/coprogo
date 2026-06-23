package tech.justdev.application.revenue

import jakarta.inject.Singleton
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.application.ledger.LedgerEventIdGenerator
import tech.justdev.application.ledger.RandomLedgerEventIdGenerator
import tech.justdev.application.shared.TransactionRunner
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.ledger.event.CashPoolIncomeLedgerEvent
import tech.justdev.domain.ledger.repository.LedgerEventRepository
import tech.justdev.domain.revenue.repository.OwnershipShareTimelineRepository
import tech.justdev.domain.revenue.valueobject.RevenueDistribution
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import java.time.Instant
import java.time.LocalDate

data class RecordCashPoolIncomeCommand(
    val group: GroupId,
    val recordedBy: MemberEmail,
    val amount: MoneyAmount,
    val receivedAt: Instant,
    val effectiveDate: LocalDate,
)

@Singleton
open class RecordCashPoolIncomeUseCase(
    private val groupAccessPolicy: GroupAccessPolicy,
    private val ownershipShareTimelineRepository: OwnershipShareTimelineRepository,
    private val ledgerEventRepository: LedgerEventRepository,
    private val transactionRunner: TransactionRunner,
    private val ledgerEventIdGenerator: LedgerEventIdGenerator = RandomLedgerEventIdGenerator,
) {
    suspend operator fun invoke(command: RecordCashPoolIncomeCommand) =
        transactionRunner.transaction {
            record(command)
        }

    private suspend fun record(command: RecordCashPoolIncomeCommand) {
        groupAccessPolicy.requireMember(command.group, command.recordedBy)

        val timeline =
            ownershipShareTimelineRepository.findByGroup(command.group)
                ?: throw IllegalArgumentException("ownership share timeline for group ${command.group.toPrimitive()} was not found")

        ledgerEventRepository.append(
            CashPoolIncomeLedgerEvent.from(
                id = ledgerEventIdGenerator.next(),
                group = command.group,
                occurredAt = command.receivedAt,
                distribution =
                    RevenueDistribution.distribute(
                        totalAmount = command.amount,
                        ownershipShares = timeline.sharesAt(command.effectiveDate),
                    ),
            ),
        )
    }
}
