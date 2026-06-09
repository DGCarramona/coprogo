package tech.justdev.testsupport

import tech.justdev.application.expense.ExpenseIdGenerator
import tech.justdev.application.group.GroupIdGenerator
import tech.justdev.application.group.GroupInvitationIdGenerator
import tech.justdev.application.revenue.OwnershipShareChangeIdGenerator
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.group.entity.GroupInvitationId
import tech.justdev.domain.revenue.entity.OwnershipShareChangeId
import tech.justdev.domain.shared.valueobject.GroupId

class FixedExpenseIdGenerator(
    private val ids: List<ExpenseId>,
) : ExpenseIdGenerator {
    private var nextIndex = 0

    override fun next(): ExpenseId =
        ids.getOrNull(nextIndex++)
            ?: throw IllegalStateException("no fixed expense id configured for index $nextIndex")
}

class FixedGroupIdGenerator(
    private val ids: List<GroupId>,
) : GroupIdGenerator {
    private var nextIndex = 0

    override fun next(): GroupId =
        ids.getOrNull(nextIndex++)
            ?: throw IllegalStateException("no fixed group id configured for index $nextIndex")
}

class FixedGroupInvitationIdGenerator(
    private val ids: List<GroupInvitationId>,
) : GroupInvitationIdGenerator {
    private var nextIndex = 0

    override fun next(): GroupInvitationId =
        ids.getOrNull(nextIndex++)
            ?: throw IllegalStateException("no fixed group invitation id configured for index $nextIndex")
}

class FixedOwnershipShareChangeIdGenerator(
    private val ids: List<OwnershipShareChangeId>,
) : OwnershipShareChangeIdGenerator {
    private var nextIndex = 0

    override fun next(): OwnershipShareChangeId =
        ids.getOrNull(nextIndex++)
            ?: throw IllegalStateException("no fixed ownership share change id configured for index $nextIndex")
}
