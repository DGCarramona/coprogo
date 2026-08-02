package tech.justdev.application.expense

import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.application.group.GroupAccessPolicy
import tech.justdev.domain.expense.entity.Expense
import tech.justdev.domain.expense.repository.ExpenseRepository
import tech.justdev.domain.expense.valueobject.ExpenseParticipationStatus
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.domain.ledger.event.LedgerEvent
import tech.justdev.domain.ledger.repository.LedgerEventRepository
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.infrastructure.persistence.jooq.R2dbcTransactionRunner
import tech.justdev.testsupport.PostgresMicronautTest
import tech.justdev.testsupport.expenseId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import java.time.Instant

@PostgresMicronautTest
class RecordExpenseParticipationDecisionUseCaseIntegrationTest {
    @Inject
    lateinit var expenseRepository: ExpenseRepository

    @Inject
    lateinit var groupRepository: GroupRepository

    @Inject
    lateinit var memberRepository: MemberRepository

    @Inject
    lateinit var transactionRunner: R2dbcTransactionRunner

    @Test
    fun `invoke should roll back the expense decision when appending the accepted expense event fails`() {
        val useCase =
            RecordExpenseParticipationDecisionUseCaseImpl(
                expenseRepository = expenseRepository,
                ledgerEventRepository = FailingLedgerEventRepository,
                groupAccessPolicy = GroupAccessPolicy(groupRepository),
                transactionRunner = transactionRunner,
            )
        var fixture: Fixture? = null

        val error =
            assertThrows<IllegalStateException> {
                runTest {
                    fixture = persistFixture()
                    useCase(
                        RecordExpenseParticipationDecisionCommand(
                            group = requireNotNull(fixture).group.id,
                            id = requireNotNull(fixture).expense.id.toPrimitive(),
                            member = requireNotNull(fixture).participant,
                            decision = ExpenseParticipationDecisionCommand.APPROVE,
                            decidedAt = Instant.parse("2026-06-01T12:00:00Z"),
                        ),
                    )
                }
            }

        assertEquals("ledger append failed", error.message)
        val persistedFixture = requireNotNull(fixture)
        val storedExpense = runBlocking { requireNotNull(expenseRepository.findById(persistedFixture.expense.id)) }
        assertEquals(persistedFixture.expense, storedExpense)
        assertEquals(
            ExpenseParticipationStatus.Pending,
            storedExpense.participations.single { it.member == persistedFixture.participant }.status,
        )
    }

    private suspend fun persistFixture(): Fixture {
        val owner = memberEmail("rollback-owner")
        val participant = memberEmail("rollback-participant")
        val group =
            Group
                .create(
                    id = groupId("rollback-group"),
                    createdBy = owner,
                    createdAt = Instant.parse("2026-06-01T10:00:00Z"),
                ).addMember(
                    member = participant,
                    joinedAt = Instant.parse("2026-06-01T10:01:00Z"),
                )
        val expense =
            Expense.proposeEqualSplit(
                id = expenseId("rollback-expense"),
                group = group.id,
                title = "Boiler repair",
                createdBy = owner,
                totalAmount = MoneyAmount.ofCents(100),
                createdAt = Instant.parse("2026-06-01T10:00:00Z"),
                participants = setOf(owner, participant),
            )

        memberRepository.persist(Member(email = owner, createdAt = Instant.parse("2026-06-01T09:00:00Z")))
        memberRepository.persist(Member(email = participant, createdAt = Instant.parse("2026-06-01T09:01:00Z")))
        groupRepository.persist(group)
        expenseRepository.persist(expense)

        return Fixture(group, expense, participant)
    }

    private data class Fixture(
        val group: Group,
        val expense: Expense,
        val participant: tech.justdev.domain.group.valueobject.MemberEmail,
    )
}

private object FailingLedgerEventRepository : LedgerEventRepository {
    override suspend fun append(event: LedgerEvent) = throw IllegalStateException("ledger append failed")

    override suspend fun findByGroup(group: GroupId): List<LedgerEvent> = throw AssertionError("ledger events should not be read")
}
