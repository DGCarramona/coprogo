package tech.justdev.application.expense

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.application.group.GroupAccessDeniedException
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.application.support.InMemoryExpenseRepository
import tech.justdev.application.support.InMemoryGroupRepository
import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.repository.ExpenseRepository
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.expense.valueobject.ExpenseParticipation
import tech.justdev.domain.expense.valueobject.ExpenseParticipationStatus
import tech.justdev.domain.expense.valueobject.ExpenseStatus
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.testsupport.expenseId
import tech.justdev.testsupport.groupId
import java.time.Instant

class GetExpenseDetailUseCaseTest {
    private val group = groupId("expense-detail-group")
    private val requestedBy = MemberEmail.of("owner@example.com")
    private val outsider = MemberEmail.of("outsider@example.com")
    private val groupRepository = InMemoryGroupRepository(listOf(testGroup()))
    private val groupAccessPolicy = GroupAccessPolicy(groupRepository)

    @Test
    fun `should return a complete snapshot with participations in deterministic member order`() =
        runTest {
            val expense = expense(group = group)
            val useCase =
                GetExpenseDetailUseCaseImpl(
                    expenseRepository = InMemoryExpenseRepository(listOf(expense)),
                    groupAccessPolicy = groupAccessPolicy,
                )

            val snapshot =
                useCase(
                    GetExpenseDetailQuery(
                        group = group,
                        id = expense.id,
                        requestedBy = requestedBy,
                    ),
                )

            assertEquals(
                ExpenseDetailSnapshot(
                    id = expense.id,
                    title = "Roof repair",
                    createdBy = requestedBy,
                    totalAmount = MoneyAmount.ofCents(6_000),
                    createdAt = Instant.parse("2026-07-01T10:00:00Z"),
                    status = ExpenseStatus.INVALIDATED,
                    participations =
                        listOf(
                            ExpenseDetailParticipationSnapshot(
                                member = MemberEmail.of("owner@example.com"),
                                amount = MoneyAmount.ofCents(2_000),
                                status = ExpenseParticipationStatus.Approved(Instant.parse("2026-07-01T10:00:00Z")),
                            ),
                            ExpenseDetailParticipationSnapshot(
                                member = MemberEmail.of("pending@example.com"),
                                amount = MoneyAmount.ofCents(2_000),
                                status = ExpenseParticipationStatus.Pending,
                            ),
                            ExpenseDetailParticipationSnapshot(
                                member = MemberEmail.of("refused@example.com"),
                                amount = MoneyAmount.ofCents(2_000),
                                status = ExpenseParticipationStatus.Refused(Instant.parse("2026-07-03T10:00:00Z")),
                            ),
                        ),
                ),
                snapshot,
            )
        }

    @Test
    fun `should throw when no expense exists for the id and group`() {
        val missingId = expenseId("missing-expense-detail")
        val useCase =
            GetExpenseDetailUseCaseImpl(
                expenseRepository = InMemoryExpenseRepository(),
                groupAccessPolicy = groupAccessPolicy,
            )

        val error =
            assertThrows<ExpenseNotFoundException> {
                runTest {
                    useCase(GetExpenseDetailQuery(group = group, id = missingId, requestedBy = requestedBy))
                }
            }

        assertEquals(missingId, error.id)
        assertEquals(group, error.group)
    }

    @Test
    fun `should throw when the expense id belongs to another group`() {
        val expense = expense(group = groupId("other-expense-detail-group"))
        val useCase =
            GetExpenseDetailUseCaseImpl(
                expenseRepository = InMemoryExpenseRepository(listOf(expense)),
                groupAccessPolicy = groupAccessPolicy,
            )

        val error =
            assertThrows<ExpenseNotFoundException> {
                runTest {
                    useCase(GetExpenseDetailQuery(group = group, id = expense.id, requestedBy = requestedBy))
                }
            }

        assertEquals(expense.id, error.id)
        assertEquals(group, error.group)
    }

    @Test
    fun `should reject a non-member before accessing the expense repository`() {
        val useCase =
            GetExpenseDetailUseCaseImpl(
                expenseRepository = failIfAccessedExpenseRepository(),
                groupAccessPolicy = groupAccessPolicy,
            )

        assertThrows<GroupAccessDeniedException> {
            runTest {
                useCase(
                    GetExpenseDetailQuery(
                        group = group,
                        id = expenseId("inaccessible-expense-detail"),
                        requestedBy = outsider,
                    ),
                )
            }
        }
    }

    private fun testGroup(): Group =
        Group.create(
            id = group,
            createdBy = requestedBy,
            createdAt = Instant.parse("2026-06-01T08:00:00Z"),
        )

    private fun expense(group: GroupId): Expense =
        Expense(
            id = expenseId("expense-detail"),
            group = group,
            title = "Roof repair",
            createdBy = requestedBy,
            totalAmount = MoneyAmount.ofCents(6_000),
            createdAt = Instant.parse("2026-07-01T10:00:00Z"),
            participations =
                setOf(
                    ExpenseParticipation(
                        member = requestedBy,
                        amount = MoneyAmount.ofCents(2_000),
                        status = ExpenseParticipationStatus.Approved(Instant.parse("2026-07-01T10:00:00Z")),
                    ),
                    ExpenseParticipation(
                        member = MemberEmail.of("refused@example.com"),
                        amount = MoneyAmount.ofCents(2_000),
                        status = ExpenseParticipationStatus.Refused(Instant.parse("2026-07-03T10:00:00Z")),
                    ),
                    ExpenseParticipation(
                        member = MemberEmail.of("pending@example.com"),
                        amount = MoneyAmount.ofCents(2_000),
                        status = ExpenseParticipationStatus.Pending,
                    ),
                ),
        )

    private fun failIfAccessedExpenseRepository(): ExpenseRepository =
        object : ExpenseRepository {
            override suspend fun findByIdAndGroup(
                id: ExpenseId,
                group: GroupId,
            ): Expense? = throw AssertionError("expense repository should not be accessed")

            override suspend fun findByGroup(group: GroupId): List<Expense> =
                throw AssertionError("expense repository should not be accessed")

            override suspend fun findProposedByIdAndGroup(
                id: ExpenseId,
                group: GroupId,
            ): Expense? = throw AssertionError("expense repository should not be accessed")

            override suspend fun persist(expense: Expense): Unit = throw AssertionError("expense repository should not be accessed")
        }
}
