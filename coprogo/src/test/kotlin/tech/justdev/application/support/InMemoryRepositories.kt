package tech.justdev.application.support

import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.repository.ExpenseRepository
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.expense.valueobject.ExpenseStatus
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.GroupInvitation
import tech.justdev.domain.group.entity.GroupInvitationId
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.GroupInvitationRepository
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.ledger.event.LedgerEvent
import tech.justdev.domain.ledger.repository.LedgerEventRepository
import tech.justdev.domain.revenue.entity.OwnershipShareTimeline
import tech.justdev.domain.revenue.repository.OwnershipShareTimelineRepository
import tech.justdev.domain.shared.valueobject.GroupId

class InMemoryExpenseRepository(
    expenses: Iterable<Expense> = emptyList(),
) : ExpenseRepository {
    private val expensesById = expenses.associateBy { expense -> expense.id }.toMutableMap()

    override suspend fun findById(id: ExpenseId): Expense? = expensesById[id]

    override suspend fun findProposedById(id: ExpenseId): Expense? =
        expensesById[id]
            ?.takeIf { expense -> expense.status == ExpenseStatus.PROPOSED }

    override suspend fun persist(expense: Expense) {
        expensesById[expense.id] = expense
    }
}

class InMemoryLedgerEventRepository(
    events: Iterable<LedgerEvent> = emptyList(),
) : LedgerEventRepository {
    private val storedEvents = events.toMutableList()

    override suspend fun append(event: LedgerEvent) {
        storedEvents += event
    }

    override suspend fun findByGroup(group: GroupId): List<LedgerEvent> = storedEvents.filter { event -> event.group == group }

    fun allEvents(): List<LedgerEvent> = storedEvents.toList()
}

class InMemoryMemberRepository(
    members: Iterable<Member> = emptyList(),
) : MemberRepository {
    private val membersByEmail = members.associateBy { member -> member.email }.toMutableMap()

    override suspend fun findByEmail(email: MemberEmail): Member? = membersByEmail[email]

    override suspend fun persist(member: Member) {
        membersByEmail.putIfAbsent(member.email, member)
    }
}

class InMemoryGroupRepository(
    groups: Iterable<Group> = emptyList(),
) : GroupRepository {
    private val groupsById = groups.associateBy { group -> group.id }.toMutableMap()

    override suspend fun findById(id: GroupId): Group? = groupsById[id]

    override suspend fun persist(group: Group) {
        groupsById[group.id] = group
    }
}

class InMemoryGroupInvitationRepository(
    invitations: Iterable<GroupInvitation> = emptyList(),
) : GroupInvitationRepository {
    private val invitationsById = invitations.associateBy { invitation -> invitation.id }.toMutableMap()

    override suspend fun findById(id: GroupInvitationId): GroupInvitation? = invitationsById[id]

    override suspend fun findPendingByGroup(group: GroupId): List<GroupInvitation> =
        invitationsById.values.filter { invitation -> invitation.group == group && invitation.isPending() }

    override suspend fun findPendingByInvitedMember(invitedMember: MemberEmail): List<GroupInvitation> =
        invitationsById.values.filter { invitation -> invitation.invitedMember == invitedMember && invitation.isPending() }

    override suspend fun persist(invitation: GroupInvitation) {
        invitationsById[invitation.id] = invitation
    }
}

class InMemoryOwnershipShareTimelineRepository(
    timelines: Iterable<OwnershipShareTimeline> = emptyList(),
) : OwnershipShareTimelineRepository {
    private val timelinesByGroup = timelines.associateBy { timeline -> timeline.group }.toMutableMap()

    override suspend fun findByGroup(group: GroupId): OwnershipShareTimeline? = timelinesByGroup[group]

    override suspend fun persist(timeline: OwnershipShareTimeline) {
        timelinesByGroup[timeline.group] = timeline
    }
}
