package tech.justdev.application.expense

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.application.group.GroupAccessDeniedException
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.application.support.InMemoryExpenseRepository
import tech.justdev.application.support.InMemoryGroupRepository
import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.expense.valueobject.ExpenseParticipation
import tech.justdev.domain.expense.valueobject.ExpenseParticipationStatus
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import java.time.Instant
import java.util.UUID

class ListGroupExpensesUseCaseTest {
    private val group = GroupId(UUID.randomUUID())
    private val otherGroup = GroupId(UUID.randomUUID())
    private val owner = MemberEmail.of("owner@example.com")
    private val participant = MemberEmail.of("participant@example.com")
    private val outsider = MemberEmail.of("outsider@example.com")

    private val groupRepository = InMemoryGroupRepository(listOf(testGroup()))
    private val expenseRepository = InMemoryExpenseRepository()
    private val groupAccessPolicy = GroupAccessPolicy(groupRepository)
    private val useCase = ListGroupExpensesUseCaseImpl(expenseRepository, groupAccessPolicy)

    @Test
    fun `should return empty list when group has no expenses`() =
        runTest {
            val result = useCase.invoke(ListGroupExpensesQuery(group = group, requestedBy = owner))
            assertTrue(result.isEmpty())
        }

    @Test
    fun `should list expenses for a group`() =
        runTest {
            val expense1 = expense(group, owner, setOf(owner, participant))
            val expense2 = expense(group, owner, setOf(owner, participant))
            val otherExpense = expense(otherGroup, owner, setOf(owner, participant))

            expenseRepository.persist(expense1)
            expenseRepository.persist(expense2)
            expenseRepository.persist(otherExpense)

            val result = useCase.invoke(ListGroupExpensesQuery(group = group, requestedBy = owner))

            assertEquals(2, result.size)
            assertEquals(setOf(expense1.id.toPrimitive(), expense2.id.toPrimitive()), result.map { it.id }.toSet())
        }

    @Test
    fun `should reject non-member with access denied`() {
        assertThrows<GroupAccessDeniedException> {
            runTest {
                useCase.invoke(ListGroupExpensesQuery(group = group, requestedBy = outsider))
            }
        }
    }

    private fun testGroup(): Group =
        Group
            .create(
                id = group,
                createdBy = owner,
                createdAt = Instant.parse("2026-06-01T08:00:00Z"),
            ).addMember(
                member = participant,
                joinedAt = Instant.parse("2026-06-01T09:00:00Z"),
            )

    private fun expense(
        group: GroupId,
        owner: MemberEmail,
        participants: Set<MemberEmail>,
    ): Expense {
        val others = participants - owner
        val participations =
            buildSet {
                add(
                    ExpenseParticipation(
                        member = owner,
                        amount = MoneyAmount.ofCents(500),
                        status = ExpenseParticipationStatus.Approved(Instant.parse("2026-06-01T10:00:00Z")),
                    ),
                )
                others.forEach { other ->
                    add(
                        ExpenseParticipation(
                            member = other,
                            amount = MoneyAmount.ofCents(500),
                            status = ExpenseParticipationStatus.Pending,
                        ),
                    )
                }
            }

        return Expense(
            id = ExpenseId(UUID.randomUUID()),
            group = group,
            title = "test expense",
            createdBy = owner,
            totalAmount = MoneyAmount.ofCents(1000),
            createdAt = Instant.parse("2026-06-01T10:00:00Z"),
            participations = participations,
        )
    }
}
