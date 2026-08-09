package tech.justdev.application.expense

import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.domain.expense.repository.ExpenseRepository
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.expense.valueobject.ExpenseParticipation
import tech.justdev.domain.expense.valueobject.ExpenseParticipationStatus
import tech.justdev.domain.expense.valueobject.ExpenseStatus
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.testsupport.FixedExpenseIdGenerator
import tech.justdev.testsupport.PostgresMicronautTest
import tech.justdev.testsupport.expenseId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import java.time.Instant

@PostgresMicronautTest
class ProposeExpenseUseCaseIntegrationTest {
    @Inject
    lateinit var expenseRepository: ExpenseRepository

    @Inject
    lateinit var groupRepository: GroupRepository

    @Inject
    lateinit var memberRepository: MemberRepository

    @Test
    fun `invoke should persist an equal allocation with deterministic remainder`() =
        runTest {
            val fixture = persistFixture("equal")
            val id = expenseId("equal-expense")
            val createdAt = Instant.parse("2026-07-01T10:00:00Z")

            useCase(id)(
                ProposeExpenseCommand(
                    group = fixture.group.id,
                    title = "Equal repair",
                    createdBy = fixture.creator,
                    totalAmountInCents = 101,
                    createdAt = createdAt,
                    allocation = EqualSplitExpenseAllocationCommand(fixture.participants),
                ),
            )

            val expense = requireNotNull(expenseRepository.findByIdAndGroup(id, fixture.group.id))
            assertEquals(ExpenseStatus.PROPOSED, expense.status)
            assertEquals(
                expectedParticipations(
                    fixture = fixture,
                    createdAt = createdAt,
                    amountsInCents = mapOf(fixture.creator to 34, fixture.second to 34, fixture.third to 33),
                ),
                expense.participations,
            )
        }

    @Test
    fun `invoke should persist an equal with caps allocation after iterative redistribution`() =
        runTest {
            val fixture = persistFixture("caps")
            val id = expenseId("caps-expense")
            val createdAt = Instant.parse("2026-07-01T11:00:00Z")

            useCase(id)(
                ProposeExpenseCommand(
                    group = fixture.group.id,
                    title = "Capped repair",
                    createdBy = fixture.creator,
                    totalAmountInCents = 100,
                    createdAt = createdAt,
                    allocation =
                        EqualSplitWithCapsExpenseAllocationCommand(
                            participants = fixture.participants,
                            capsInCentsByMember = mapOf(fixture.second to 20, fixture.third to 35),
                        ),
                ),
            )

            val expense = requireNotNull(expenseRepository.findByIdAndGroup(id, fixture.group.id))
            assertEquals(ExpenseStatus.PROPOSED, expense.status)
            assertEquals(
                expectedParticipations(
                    fixture = fixture,
                    createdAt = createdAt,
                    amountsInCents = mapOf(fixture.creator to 45, fixture.second to 20, fixture.third to 35),
                ),
                expense.participations,
            )
        }

    @Test
    fun `invoke should persist a cumulative tiers allocation across multiple tiers`() =
        runTest {
            val fixture = persistFixture("tiers")
            val id = expenseId("tiers-expense")
            val createdAt = Instant.parse("2026-07-01T12:00:00Z")

            useCase(id)(
                ProposeExpenseCommand(
                    group = fixture.group.id,
                    title = "Tiered repair",
                    createdBy = fixture.creator,
                    totalAmountInCents = 101,
                    createdAt = createdAt,
                    allocation =
                        CumulativeTiersExpenseAllocationCommand(
                            tiers =
                                listOf(
                                    CumulativeExpenseTierCommand(
                                        upToAmountInCents = 40,
                                        participants = setOf(fixture.creator, fixture.second),
                                    ),
                                    CumulativeExpenseTierCommand(
                                        upToAmountInCents = 101,
                                        participants = fixture.participants,
                                    ),
                                ),
                        ),
                ),
            )

            val expense = requireNotNull(expenseRepository.findByIdAndGroup(id, fixture.group.id))
            assertEquals(ExpenseStatus.PROPOSED, expense.status)
            assertEquals(
                expectedParticipations(
                    fixture = fixture,
                    createdAt = createdAt,
                    amountsInCents = mapOf(fixture.creator to 41, fixture.second to 40, fixture.third to 20),
                ),
                expense.participations,
            )
        }

    @Test
    fun `invoke should persist exact custom allocations for multiple participants`() =
        runTest {
            val fixture = persistFixture("custom")
            val id = expenseId("custom-expense")
            val createdAt = Instant.parse("2026-07-01T13:00:00Z")

            useCase(id)(
                ProposeExpenseCommand(
                    group = fixture.group.id,
                    title = "Custom repair",
                    createdBy = fixture.creator,
                    totalAmountInCents = 100,
                    createdAt = createdAt,
                    allocation =
                        CustomExpenseAllocationCommand(
                            participations =
                                setOf(
                                    CustomExpenseParticipationCommand(fixture.creator, 17),
                                    CustomExpenseParticipationCommand(fixture.second, 28),
                                    CustomExpenseParticipationCommand(fixture.third, 55),
                                ),
                        ),
                ),
            )

            val expense = requireNotNull(expenseRepository.findByIdAndGroup(id, fixture.group.id))
            assertEquals(ExpenseStatus.PROPOSED, expense.status)
            assertEquals(
                expectedParticipations(
                    fixture = fixture,
                    createdAt = createdAt,
                    amountsInCents = mapOf(fixture.creator to 17, fixture.second to 28, fixture.third to 55),
                ),
                expense.participations,
            )
        }

    private fun useCase(id: ExpenseId): ProposeExpenseUseCase =
        ProposeExpenseUseCaseImpl(
            expenseRepository = expenseRepository,
            groupAccessPolicy = GroupAccessPolicy(groupRepository),
            expenseIdGenerator = FixedExpenseIdGenerator(listOf(id)),
        )

    private suspend fun persistFixture(seed: String): Fixture {
        val creator = memberEmail("$seed-alice")
        val second = memberEmail("$seed-bob")
        val third = memberEmail("$seed-carol")
        listOf(creator, second, third).forEachIndexed { index, member ->
            memberRepository.persist(
                Member(
                    email = member,
                    createdAt = Instant.parse("2026-07-01T09:00:00Z").plusSeconds(index.toLong()),
                ),
            )
        }
        val group =
            Group
                .create(
                    id = groupId("$seed-group"),
                    createdBy = creator,
                    createdAt = Instant.parse("2026-07-01T09:10:00Z"),
                ).addMember(
                    member = second,
                    joinedAt = Instant.parse("2026-07-01T09:11:00Z"),
                ).addMember(
                    member = third,
                    joinedAt = Instant.parse("2026-07-01T09:12:00Z"),
                )
        groupRepository.persist(group)

        return Fixture(group = group, creator = creator, second = second, third = third)
    }

    private fun expectedParticipations(
        fixture: Fixture,
        createdAt: Instant,
        amountsInCents: Map<MemberEmail, Long>,
    ): Set<ExpenseParticipation> =
        amountsInCents
            .map { (member, amountInCents) ->
                ExpenseParticipation(
                    member = member,
                    amount = MoneyAmount.ofCents(amountInCents),
                    status =
                        if (member == fixture.creator) {
                            ExpenseParticipationStatus.Approved(createdAt)
                        } else {
                            ExpenseParticipationStatus.Pending
                        },
                )
            }.toSet()

    private data class Fixture(
        val group: Group,
        val creator: MemberEmail,
        val second: MemberEmail,
        val third: MemberEmail,
    ) {
        val participants: Set<MemberEmail> = setOf(creator, second, third)
    }
}
