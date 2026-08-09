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
import tech.justdev.domain.expense.valueobject.ExpenseStatus
import tech.justdev.domain.expense.valueobject.RefusalReason
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.domain.ledger.effect.MemberBalanceTransfer
import tech.justdev.domain.ledger.event.AcceptedExpenseLedgerEvent
import tech.justdev.domain.ledger.event.LedgerEvent
import tech.justdev.domain.ledger.repository.LedgerEventRepository
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.infrastructure.persistence.jooq.R2dbcTransactionRunner
import tech.justdev.testsupport.PostgresMicronautTest
import tech.justdev.testsupport.acceptedExpenseLedgerEventId
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
    lateinit var ledgerEventRepository: LedgerEventRepository

    @Inject
    lateinit var transactionRunner: R2dbcTransactionRunner

    @Test
    fun `invoke should accept the expense and append exactly one accepted expense ledger event`() =
        runTest {
            val fixture = persistFixture("accepted")
            val decidedAt = Instant.parse("2026-06-01T12:00:00Z")
            val useCase = useCase(ledgerEventRepository)

            useCase(
                RecordExpenseParticipationDecisionCommand(
                    group = fixture.group.id,
                    id = fixture.expense.id.toPrimitive(),
                    member = fixture.participant,
                    decision = ExpenseParticipationDecisionCommand.APPROVE,
                    decidedAt = decidedAt,
                ),
            )

            val storedExpense =
                requireNotNull(expenseRepository.findByIdAndGroup(fixture.expense.id, fixture.group.id))
            assertEquals(ExpenseStatus.ACCEPTED, storedExpense.status)
            assertEquals(
                listOf(
                    AcceptedExpenseLedgerEvent(
                        id = acceptedExpenseLedgerEventId("accepted-expense"),
                        group = fixture.group.id,
                        expense = fixture.expense.id,
                        paidBy = fixture.group.createdBy,
                        occurredAt = decidedAt,
                        transfers =
                            setOf(
                                MemberBalanceTransfer(
                                    fromMember = fixture.participant,
                                    toMember = fixture.group.createdBy,
                                    amount = MoneyAmount.ofCents(50),
                                ),
                            ),
                    ),
                ),
                ledgerEventRepository.findByGroup(fixture.group.id),
            )
        }

    @Test
    fun `invoke should invalidate the expense with its refusal reason and append no ledger event`() =
        runTest {
            val fixture = persistFixture("refused")
            val decidedAt = Instant.parse("2026-06-01T12:30:00Z")
            val reason = RefusalReason.of("The amount does not match the invoice")
            val useCase = useCase(ledgerEventRepository)

            useCase(
                RecordExpenseParticipationDecisionCommand(
                    group = fixture.group.id,
                    id = fixture.expense.id.toPrimitive(),
                    member = fixture.participant,
                    decision = ExpenseParticipationDecisionCommand.REFUSE,
                    decidedAt = decidedAt,
                    reason = reason,
                ),
            )

            val storedExpense =
                requireNotNull(expenseRepository.findByIdAndGroup(fixture.expense.id, fixture.group.id))
            assertEquals(ExpenseStatus.INVALIDATED, storedExpense.status)
            assertEquals(
                ExpenseParticipationStatus.Refused(decidedAt = decidedAt, reason = reason),
                storedExpense.participations.single { participation -> participation.member == fixture.participant }.status,
            )
            assertEquals(emptyList<LedgerEvent>(), ledgerEventRepository.findByGroup(fixture.group.id))
        }

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
                    fixture = persistFixture("rollback")
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
        val storedExpense =
            runBlocking {
                requireNotNull(
                    expenseRepository.findByIdAndGroup(
                        persistedFixture.expense.id,
                        persistedFixture.expense.group,
                    ),
                )
            }
        assertEquals(persistedFixture.expense, storedExpense)
        assertEquals(
            ExpenseParticipationStatus.Pending,
            storedExpense.participations.single { it.member == persistedFixture.participant }.status,
        )
    }

    private fun useCase(ledgerEventRepository: LedgerEventRepository): RecordExpenseParticipationDecisionUseCase =
        RecordExpenseParticipationDecisionUseCaseImpl(
            expenseRepository = expenseRepository,
            ledgerEventRepository = ledgerEventRepository,
            groupAccessPolicy = GroupAccessPolicy(groupRepository),
            transactionRunner = transactionRunner,
        )

    private suspend fun persistFixture(seed: String): Fixture {
        val owner = memberEmail("$seed-owner")
        val participant = memberEmail("$seed-participant")
        val group =
            Group
                .create(
                    id = groupId("$seed-group"),
                    createdBy = owner,
                    createdAt = Instant.parse("2026-06-01T10:00:00Z"),
                ).addMember(
                    member = participant,
                    joinedAt = Instant.parse("2026-06-01T10:01:00Z"),
                )
        val expense =
            Expense.proposeEqualSplit(
                id = expenseId("$seed-expense"),
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
