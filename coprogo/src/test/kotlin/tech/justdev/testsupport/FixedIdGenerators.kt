package tech.justdev.testsupport

import tech.justdev.application.expense.ExpenseIdGenerator
import tech.justdev.application.revenue.OwnershipShareChangeIdGenerator
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.revenue.entity.OwnershipShareChangeId

class FixedExpenseIdGenerator(
    private val ids: List<ExpenseId>,
) : ExpenseIdGenerator {
    private var nextIndex = 0

    override fun next(): ExpenseId {
        return ids.getOrNull(nextIndex++)
            ?: throw IllegalStateException("no fixed expense id configured for index $nextIndex")
    }
}

class FixedOwnershipShareChangeIdGenerator(
    private val ids: List<OwnershipShareChangeId>,
) : OwnershipShareChangeIdGenerator {
    private var nextIndex = 0

    override fun next(): OwnershipShareChangeId {
        return ids.getOrNull(nextIndex++)
            ?: throw IllegalStateException("no fixed ownership share change id configured for index $nextIndex")
    }
}
