package tech.justdev.application.support

import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.expense.repository.ExpenseRepository
import tech.justdev.domain.expense.valueobject.ExpenseStatus
import tech.justdev.domain.ledger.event.LedgerEvent
import tech.justdev.domain.ledger.repository.LedgerEventRepository
import tech.justdev.domain.revenue.entity.OwnershipShareTimeline
import tech.justdev.domain.revenue.repository.OwnershipShareTimelineRepository
import tech.justdev.domain.shared.valueobject.GroupId

class InMemoryExpenseRepository(
    expenses: Iterable<Expense> = emptyList(),
) : ExpenseRepository {
    private val expensesById = expenses.associateBy { expense -> expense.id }.toMutableMap()

    override fun findById(expenseId: ExpenseId): Expense? = expensesById[expenseId]

    override fun findProposedById(expenseId: ExpenseId): Expense? =
        expensesById[expenseId]
            ?.takeIf { expense -> expense.status == ExpenseStatus.PROPOSED }

    override fun save(expense: Expense) {
        expensesById[expense.id] = expense
    }
}

class InMemoryLedgerEventRepository(
    events: Iterable<LedgerEvent> = emptyList(),
) : LedgerEventRepository {
    private val storedEvents = events.toMutableList()

    override fun append(event: LedgerEvent) {
        storedEvents += event
    }

    override fun findByGroup(group: GroupId): List<LedgerEvent> =
        storedEvents.filter { event -> event.group == group }

    fun allEvents(): List<LedgerEvent> = storedEvents.toList()
}

class InMemoryOwnershipShareTimelineRepository(
    timelines: Iterable<OwnershipShareTimeline> = emptyList(),
) : OwnershipShareTimelineRepository {
    private val timelinesByGroup = timelines.associateBy { timeline -> timeline.group }.toMutableMap()

    override fun findByGroup(group: GroupId): OwnershipShareTimeline? = timelinesByGroup[group]

    override fun save(timeline: OwnershipShareTimeline) {
        timelinesByGroup[timeline.group] = timeline
    }
}
