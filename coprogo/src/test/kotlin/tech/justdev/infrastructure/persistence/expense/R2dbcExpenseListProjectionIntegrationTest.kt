package tech.justdev.infrastructure.persistence.expense

import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tech.justdev.application.expense.ExpenseListProjection
import tech.justdev.application.expense.ExpenseRefusalSnapshot
import tech.justdev.application.expense.ExpenseSnapshot
import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.repository.ExpenseRepository
import tech.justdev.domain.expense.valueobject.ExpenseParticipation
import tech.justdev.domain.expense.valueobject.ExpenseParticipationStatus
import tech.justdev.domain.expense.valueobject.RefusalReason
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.testsupport.PostgresMicronautTest
import tech.justdev.testsupport.expenseId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import java.time.Instant
import java.util.UUID

@PostgresMicronautTest
class R2dbcExpenseListProjectionIntegrationTest {
    @Inject
    lateinit var projection: ExpenseListProjection

    @Inject
    lateinit var expenseRepository: ExpenseRepository

    @Inject
    lateinit var groupRepository: GroupRepository

    @Inject
    lateinit var memberRepository: MemberRepository

    @Nested
    inner class FindByGroup {
        @Test
        fun `returns ordered group-scoped summaries and the refusal without hydrating aggregates`() =
            runTest {
                val fixture = persistFixture()

                val result = projection.findByGroup(fixture.group)

                assertEquals(
                    listOf(
                        ExpenseSnapshot(
                            id = fixture.accepted.id.toPrimitive(),
                            title = fixture.accepted.title,
                            createdBy = fixture.owner.toPrimitive(),
                            totalAmountCents = fixture.accepted.totalAmount.inCents(),
                            createdAt = fixture.accepted.createdAt,
                            status = "ACCEPTED",
                        ),
                        ExpenseSnapshot(
                            id = fixture.refused.id.toPrimitive(),
                            title = fixture.refused.title,
                            createdBy = fixture.owner.toPrimitive(),
                            totalAmountCents = fixture.refused.totalAmount.inCents(),
                            createdAt = fixture.refused.createdAt,
                            status = "INVALIDATED",
                            refusal =
                                ExpenseRefusalSnapshot(
                                    member = fixture.participant,
                                    refusedAt = REFUSED_AT,
                                    reason = REFUSAL_REASON,
                                ),
                        ),
                    ),
                    result,
                )
            }

        @Test
        fun `returns an empty list for a group without expenses`() =
            runTest {
                val seed = UUID.randomUUID().toString()
                val owner = memberEmail("$seed-empty-owner")
                val group = groupId("empty-group-$seed")
                memberRepository.persist(Member(owner, CREATED_AT.minusSeconds(60)))
                groupRepository.persist(Group.create(group, owner, CREATED_AT))

                assertEquals(emptyList<ExpenseSnapshot>(), projection.findByGroup(group))
            }
    }

    private suspend fun persistFixture(): Fixture {
        val seed = UUID.randomUUID().toString()
        val owner = memberEmail("$seed-owner")
        val participant = memberEmail("$seed-participant")
        val group = groupId("group-$seed")
        val otherGroup = groupId("other-group-$seed")
        memberRepository.persist(Member(owner, CREATED_AT.minusSeconds(120)))
        memberRepository.persist(Member(participant, CREATED_AT.minusSeconds(90)))
        groupRepository.persist(
            Group
                .create(group, owner, CREATED_AT.minusSeconds(60))
                .addMember(participant, CREATED_AT.minusSeconds(30)),
        )
        groupRepository.persist(Group.create(otherGroup, owner, CREATED_AT.minusSeconds(60)))

        val accepted =
            expense(
                seed = "accepted-$seed",
                group = group,
                owner = owner,
                participant = participant,
                createdAt = CREATED_AT,
                participantStatus = ExpenseParticipationStatus.Approved(ACCEPTED_AT),
            )
        val refused =
            expense(
                seed = "refused-$seed",
                group = group,
                owner = owner,
                participant = participant,
                createdAt = CREATED_AT.plusSeconds(60),
                participantStatus = ExpenseParticipationStatus.Refused(REFUSED_AT, REFUSAL_REASON),
            )
        val other =
            expense(
                seed = "other-$seed",
                group = otherGroup,
                owner = owner,
                participant = owner,
                createdAt = CREATED_AT.plusSeconds(120),
                participantStatus = ExpenseParticipationStatus.Approved(CREATED_AT.plusSeconds(120)),
            )
        expenseRepository.persist(accepted)
        expenseRepository.persist(refused)
        expenseRepository.persist(other)

        return Fixture(group, owner, participant, accepted, refused)
    }

    private fun expense(
        seed: String,
        group: GroupId,
        owner: MemberEmail,
        participant: MemberEmail,
        createdAt: Instant,
        participantStatus: ExpenseParticipationStatus,
    ): Expense {
        val participations =
            if (participant == owner) {
                setOf(ExpenseParticipation(owner, MoneyAmount.ofCents(1_000), participantStatus))
            } else {
                setOf(
                    ExpenseParticipation(
                        owner,
                        MoneyAmount.ofCents(500),
                        ExpenseParticipationStatus.Approved(createdAt),
                    ),
                    ExpenseParticipation(participant, MoneyAmount.ofCents(500), participantStatus),
                )
            }
        return Expense(
            id = expenseId(seed),
            group = group,
            title = seed,
            createdBy = owner,
            totalAmount = MoneyAmount.ofCents(1_000),
            createdAt = createdAt,
            participations = participations,
        )
    }

    private data class Fixture(
        val group: GroupId,
        val owner: MemberEmail,
        val participant: MemberEmail,
        val accepted: Expense,
        val refused: Expense,
    )

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-06-01T10:00:00Z")
        val ACCEPTED_AT: Instant = Instant.parse("2026-06-01T11:00:00Z")
        val REFUSED_AT: Instant = Instant.parse("2026-06-02T10:00:00Z")
        val REFUSAL_REASON: RefusalReason = RefusalReason.of("The work was not agreed")
    }
}
