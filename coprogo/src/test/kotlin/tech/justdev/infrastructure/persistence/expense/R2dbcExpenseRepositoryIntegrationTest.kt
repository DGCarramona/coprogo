package tech.justdev.infrastructure.persistence.expense

import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.repository.ExpenseRepository
import tech.justdev.domain.expense.valueobject.ExpenseParticipationDecision
import tech.justdev.domain.expense.valueobject.ExpenseShare
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.testsupport.PostgresMicronautTest
import tech.justdev.testsupport.expenseId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import java.time.Instant

@PostgresMicronautTest
class R2dbcExpenseRepositoryIntegrationTest {
    @Inject
    lateinit var expenseRepository: ExpenseRepository

    @Inject
    lateinit var memberRepository: MemberRepository

    @Inject
    lateinit var groupRepository: GroupRepository

    @Nested
    inner class Persist {
        @Test
        fun `should persist and retrieve an expense with participations`() =
            runTest {
                val stored = expenseWithEqualSplit("persist-expense")

                expenseRepository.persist(stored)

                assertEquals(stored, expenseRepository.findById(stored.id))
            }

        @Test
        fun `should replace stored participations on second persist`() =
            runTest {
                val owner = memberEmail("replace-persist-owner")
                val firstMember = memberEmail("replace-persist-first")
                val group = groupId("replace-persist-group")
                persistMember(owner)
                persistMember(firstMember)
                persistGroup(group, owner)

                val initial =
                    Expense.proposeEqualSplit(
                        id = expenseId("replace-persist"),
                        group = group,
                        title = "replace",
                        createdBy = owner,
                        totalAmount = MoneyAmount.ofCents(1000),
                        createdAt = Instant.parse("2026-06-01T10:00:00Z"),
                        participants = setOf(owner, firstMember),
                    )

                expenseRepository.persist(initial)

                val secondMember = memberEmail("replace-persist-second")
                persistMember(secondMember)

                val updated =
                    Expense.propose(
                        id = expenseId("replace-persist"),
                        group = group,
                        title = "replace",
                        createdBy = owner,
                        totalAmount = MoneyAmount.ofCents(1500),
                        createdAt = Instant.parse("2026-06-01T10:00:00Z"),
                        shares =
                            setOf(
                                ExpenseShare(owner, MoneyAmount.ofCents(1000)),
                                ExpenseShare(secondMember, MoneyAmount.ofCents(500)),
                            ),
                    )

                expenseRepository.persist(updated)

                assertEquals(updated, expenseRepository.findById(updated.id))
            }
    }

    @Nested
    inner class FindById {
        @Test
        fun `should find a persisted expense with participations`() =
            runTest {
                val stored = expenseWithEqualSplit("find-expense")
                expenseRepository.persist(stored)

                assertEquals(stored, expenseRepository.findById(stored.id))
            }

        @Test
        fun `should return null when no expense exists for the id`() =
            runTest {
                assertNull(expenseRepository.findById(expenseId("missing-expense")))
            }
    }

    @Nested
    inner class FindProposedByIdAndGroup {
        @Test
        fun `should return null when the expense is accepted`() =
            runTest {
                val stored = expenseWithEqualSplit("proposed-expense-accepted")
                expenseRepository.persist(stored)

                val participant = memberEmail("proposed-expense-accepted-participant")
                val updated =
                    stored.recordParticipationDecision(
                        member = participant,
                        decision = ExpenseParticipationDecision.APPROVE,
                        decidedAt = Instant.parse("2026-06-01T12:00:00Z"),
                    )

                expenseRepository.persist(updated)

                assertNull(expenseRepository.findProposedByIdAndGroup(updated.id, updated.group))
            }

        @Test
        fun `should return null when the expense is invalidated`() =
            runTest {
                val stored = expenseWithEqualSplit("proposed-expense-refused")
                expenseRepository.persist(stored)

                val participant = memberEmail("proposed-expense-refused-participant")
                val updated =
                    stored.recordParticipationDecision(
                        member = participant,
                        decision = ExpenseParticipationDecision.REFUSE,
                        decidedAt = Instant.parse("2026-06-01T12:00:00Z"),
                    )

                expenseRepository.persist(updated)

                assertNull(expenseRepository.findProposedByIdAndGroup(updated.id, updated.group))
            }

        @Test
        fun `should return the expense when still proposed`() =
            runTest {
                val stored = expenseWithEqualSplit("proposed-expense-pending")
                expenseRepository.persist(stored)

                assertEquals(stored, expenseRepository.findProposedByIdAndGroup(stored.id, stored.group))
            }

        @Test
        fun `should return null when the expense id belongs to another group`() =
            runTest {
                val stored = expenseWithEqualSplit("proposed-expense-other-group")
                expenseRepository.persist(stored)

                assertNull(
                    expenseRepository.findProposedByIdAndGroup(
                        stored.id,
                        groupId("requested-other-group"),
                    ),
                )
            }
    }

    @Nested
    inner class FindByGroup {
        @Test
        fun `should return expenses for a specific group`() =
            runTest {
                val groupA = groupId("ga")
                val groupB = groupId("gb")
                val owner = memberEmail("findbygroup-owner")
                persistMember(owner)
                persistGroup(groupA, owner)
                persistGroup(groupB, owner)

                val expense1 =
                    expenseWithEqualSplit(
                        seed = "fbge1",
                        groupOverride = groupA,
                        ownerOverride = owner,
                    )
                val expense2 =
                    expenseWithEqualSplit(
                        seed = "fbge2",
                        groupOverride = groupA,
                        ownerOverride = owner,
                    )
                expenseRepository.persist(expense1)
                expenseRepository.persist(expense2)

                val expense3 =
                    expenseWithEqualSplit(
                        seed = "fbge3",
                        groupOverride = groupB,
                        ownerOverride = owner,
                    )
                expenseRepository.persist(expense3)

                val groupAExpenses = expenseRepository.findByGroup(groupA)
                assertEquals(setOf(expense1, expense2), groupAExpenses.toSet())

                val groupBExpenses = expenseRepository.findByGroup(groupB)
                assertEquals(setOf(expense3), groupBExpenses.toSet())
            }

        @Test
        fun `should return empty list when group has no expenses`() =
            runTest {
                val emptyGroup = groupId("fbge")
                val owner = memberEmail("fbge-owner")
                persistMember(owner)
                persistGroup(emptyGroup, owner)

                val result = expenseRepository.findByGroup(emptyGroup)
                assertTrue(result.isEmpty())
            }
    }

    private suspend fun expenseWithEqualSplit(
        seed: String,
        groupOverride: GroupId? = null,
        ownerOverride: tech.justdev.domain.group.valueobject.MemberEmail? = null,
    ): Expense {
        val owner = ownerOverride ?: memberEmail("$seed-owner")
        val participant = memberEmail("$seed-participant")
        val group = groupOverride ?: groupId(seed)
        persistMember(owner)
        persistMember(participant)
        persistGroup(group, owner)

        return Expense.proposeEqualSplit(
            id = expenseId(seed),
            group = group,
            title = seed,
            createdBy = owner,
            totalAmount = MoneyAmount.ofCents(1000),
            createdAt = Instant.parse("2026-06-01T10:00:00Z"),
            participants = setOf(owner, participant),
        )
    }

    private suspend fun persistGroup(
        id: GroupId,
        owner: tech.justdev.domain.group.valueobject.MemberEmail,
    ) {
        groupRepository.persist(Group.create(id = id, createdBy = owner, createdAt = Instant.parse("2026-04-13T10:00:00Z")))
    }

    private suspend fun persistMember(email: tech.justdev.domain.group.valueobject.MemberEmail) {
        memberRepository.persist(
            Member(
                email = email,
                createdAt = Instant.parse("2026-04-13T10:00:00Z"),
            ),
        )
    }
}
