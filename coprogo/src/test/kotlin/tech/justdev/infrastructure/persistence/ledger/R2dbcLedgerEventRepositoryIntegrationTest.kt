package tech.justdev.infrastructure.persistence.ledger

import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Inject
import jakarta.inject.Named
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.domain.expense.valueobject.ExpenseId
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.domain.ledger.effect.MemberBalanceTransfer
import tech.justdev.domain.ledger.event.AcceptedExpenseLedgerEvent
import tech.justdev.domain.ledger.event.AcceptedReimbursementLedgerEvent
import tech.justdev.domain.ledger.event.CashPoolIncomeLedgerEvent
import tech.justdev.domain.ledger.event.CashPoolWithdrawalLedgerEvent
import tech.justdev.domain.ledger.event.LedgerEvent
import tech.justdev.domain.ledger.repository.LedgerEventRepository
import tech.justdev.domain.reimbursement.entity.Reimbursement
import tech.justdev.domain.reimbursement.repository.ReimbursementRepository
import tech.justdev.domain.reimbursement.valueobject.ReimbursementId
import tech.justdev.domain.revenue.valueobject.OwnershipPercentage
import tech.justdev.domain.revenue.valueobject.OwnershipShare
import tech.justdev.domain.revenue.valueobject.RevenueDistribution
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.infrastructure.persistence.jooq.Tables.LEDGER_MEMBER_BALANCE_TRANSFERS
import tech.justdev.infrastructure.persistence.jooq.Tables.LEDGER_MEMBER_CASH_POOL_SHARE_DELTAS
import tech.justdev.infrastructure.persistence.jooq.awaitList
import tech.justdev.infrastructure.persistence.jooq.dsl
import tech.justdev.testsupport.PostgresMicronautTest
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.ledgerEventId
import tech.justdev.testsupport.memberEmail
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

@PostgresMicronautTest
class R2dbcLedgerEventRepositoryIntegrationTest {
    @Inject
    @field:Named("default")
    lateinit var connectionFactory: ConnectionFactory

    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var ledgerEventRepository: LedgerEventRepository

    @Inject
    lateinit var memberRepository: MemberRepository

    @Inject
    lateinit var groupRepository: GroupRepository

    @Inject
    lateinit var reimbursementRepository: ReimbursementRepository

    @Nested
    inner class Append {
        @Test
        fun `should append cash-pool income and revenue distribution events`() =
            runTest {
                val fixture = persistedGroupFixture("la")
                val events = fixture.revenueEvents()

                events.forEach { event -> ledgerEventRepository.append(event) }

                assertEquals(events, ledgerEventRepository.findByGroup(fixture.group.id))
            }

        @Test
        fun `stores withdrawals without derived transfers or share deltas and preserves income allocations`() =
            runTest {
                val fixture = persistedGroupFixture("lb")
                val withdrawal = fixture.withdrawalEvent()
                val income = fixture.incomeEvent("income", "2026-04-03T10:00:00Z", 101)

                ledgerEventRepository.append(income)
                ledgerEventRepository.append(withdrawal)

                assertEquals(
                    listOf(income, withdrawal.copy(ownRevenueShareConsumed = withdrawal.withdrawnAmount, balanceTransfers = emptySet())),
                    ledgerEventRepository.findByGroup(fixture.group.id),
                )
                val dsl = connectionFactory.dsl()
                assertEquals(
                    emptyList<Any>(),
                    dsl
                        .selectFrom(LEDGER_MEMBER_BALANCE_TRANSFERS)
                        .where(LEDGER_MEMBER_BALANCE_TRANSFERS.EVENT.eq(withdrawal.id.toPrimitive()))
                        .awaitList(),
                )
                assertEquals(
                    emptyList<Any>(),
                    dsl
                        .selectFrom(LEDGER_MEMBER_CASH_POOL_SHARE_DELTAS)
                        .where(LEDGER_MEMBER_CASH_POOL_SHARE_DELTAS.EVENT.eq(withdrawal.id.toPrimitive()))
                        .awaitList(),
                )
                assertEquals(
                    income.allocations.map { it.member.toPrimitive() to it.amount.inCents() }.sortedBy { it.first },
                    dsl
                        .select(LEDGER_MEMBER_CASH_POOL_SHARE_DELTAS.MEMBER_EMAIL, LEDGER_MEMBER_CASH_POOL_SHARE_DELTAS.AMOUNT_IN_CENTS)
                        .from(LEDGER_MEMBER_CASH_POOL_SHARE_DELTAS)
                        .where(LEDGER_MEMBER_CASH_POOL_SHARE_DELTAS.EVENT.eq(income.id.toPrimitive()))
                        .orderBy(LEDGER_MEMBER_CASH_POOL_SHARE_DELTAS.MEMBER_EMAIL)
                        .awaitList()
                        .map { it.value1() to it.value2() },
                )
            }

        @Test
        fun `should append accepted expense events with their balance transfers`() =
            runTest {
                val fixture = persistedGroupFixture("accepted-expense")
                val event =
                    AcceptedExpenseLedgerEvent(
                        id = ledgerEventId("${fixture.seed}-accepted-expense"),
                        group = fixture.group.id,
                        expense = ExpenseId(tech.justdev.testsupport.testUuid("${fixture.seed}-expense")),
                        paidBy = fixture.owner,
                        occurredAt = Instant.parse("2026-04-03T12:00:00Z"),
                        transfers =
                            setOf(
                                MemberBalanceTransfer(
                                    fromMember = fixture.coOwner,
                                    toMember = fixture.owner,
                                    amount = MoneyAmount.ofCents(45),
                                ),
                            ),
                    )

                ledgerEventRepository.append(event)

                assertEquals(listOf(event), ledgerEventRepository.findByGroup(fixture.group.id))
            }

        @Test
        fun `should append accepted reimbursement events with their debt reduction transfer`() =
            runTest {
                val fixture = persistedGroupFixture("accepted-reimbursement")
                val reimbursement = fixture.acceptedReimbursement()
                val event = AcceptedReimbursementLedgerEvent.from(reimbursement)
                reimbursementRepository.persist(reimbursement)

                ledgerEventRepository.append(event)

                assertEquals(listOf(event), ledgerEventRepository.findByGroup(fixture.group.id))
            }

        @Test
        fun `should allow only one ledger event for an accepted reimbursement`() =
            runTest {
                val fixture = persistedGroupFixture("unique-accepted-reimbursement")
                val reimbursement = fixture.acceptedReimbursement()
                val event = AcceptedReimbursementLedgerEvent.from(reimbursement)
                reimbursementRepository.persist(reimbursement)
                ledgerEventRepository.append(event)

                assertThrows<RuntimeException> {
                    runTest {
                        ledgerEventRepository.append(event.copy(id = ledgerEventId("duplicate-accepted-reimbursement")))
                    }
                }
                assertEquals(listOf(event), ledgerEventRepository.findByGroup(fixture.group.id))
            }
    }

    @Nested
    inner class FindByGroup {
        @Test
        fun `should return events for the requested group ordered by occurrence date`() =
            runTest {
                val firstGroup = persistedGroupFixture("lc")
                val secondGroup = persistedGroupFixture("ld")
                val laterEvent = firstGroup.incomeEvent("later", "2026-04-03T12:00:00Z", 100)
                val earlierEvent = firstGroup.incomeEvent("earlier", "2026-04-03T10:00:00Z", 50)
                val otherGroupEvent = secondGroup.incomeEvent("other", "2026-04-03T11:00:00Z", 75)

                listOf(laterEvent, earlierEvent, otherGroupEvent).forEach { event -> ledgerEventRepository.append(event) }

                assertEquals(listOf(earlierEvent, laterEvent), ledgerEventRepository.findByGroup(firstGroup.group.id))
            }

        @Test
        fun `should return an empty list when no event exists for the group`() =
            runTest {
                val fixture = persistedGroupFixture("le")

                assertEquals(emptyList<LedgerEvent>(), ledgerEventRepository.findByGroup(fixture.group.id))
            }
    }

    @Nested
    inner class Migration {
        @Test
        fun `preserves events and income allocations while removing withdrawal derived data`() =
            withLegacySchema { schema, migrate ->
                seedLegacyEvents(schema, incomeAt = "2026-04-03 00:30:00+02")
                val preserved = preservedLegacyData(schema)

                migrate()

                assertEquals(
                    listOf(3L, 1L, 1L, 1L),
                    legacyCounts(schema),
                )
                assertEquals(preserved, preservedLegacyData(schema))
                assertEquals(0, legacyColumnCount(schema))
            }

        @Test
        fun `rejects an impossible earlier withdrawal without changing legacy data`() =
            withLegacySchema { schema, migrate ->
                seedLegacyEvents(schema, incomeAt = "2026-04-02 23:30:00-02")
                val before = legacyCounts(schema)
                val preserved = preservedLegacyData(schema)

                assertThrows<FlywayException> { migrate() }

                assertEquals(listOf(3L, 1L, 2L, 2L), before)
                assertEquals(before, legacyCounts(schema))
                assertEquals(preserved, preservedLegacyData(schema))
                assertEquals(1, legacyColumnCount(schema))
            }
    }

    private fun withLegacySchema(block: (String, () -> Unit) -> Unit) {
        val schema = "ledger_migration_${UUID.randomUUID().toString().replace("-", "")}"

        fun flyway(target: String? = null): Flyway {
            val configuration =
                Flyway
                    .configure()
                    .dataSource(dataSource)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .cleanDisabled(false)
            if (target != null) configuration.target(target)
            return configuration.load()
        }
        try {
            flyway("18").migrate()
            block(schema) { flyway().migrate() }
        } finally {
            flyway().clean()
        }
    }

    private fun seedLegacyEvents(
        schema: String,
        incomeAt: String,
    ) {
        dataSource.connection.use { connection ->
            connection.schema = schema
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    INSERT INTO members VALUES ('owner@example.com', now()), ('other@example.com', now());
                    INSERT INTO groups (id, created_by, created_at)
                    VALUES ('00000000-0000-0000-0000-000000000001', 'owner@example.com', now());
                    INSERT INTO ledger_events VALUES
                      ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'CASH_POOL_INCOME', '$incomeAt'),
                      ('00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 'CASH_POOL_WITHDRAWAL', '2026-04-02 10:00:00+00'),
                      ('00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001', 'ACCEPTED_EXPENSE', '2026-04-01 10:00:00+00');
                    INSERT INTO ledger_cash_pool_income_events VALUES ('00000000-0000-0000-0000-000000000002', 100);
                    INSERT INTO ledger_cash_pool_withdrawal_events VALUES ('00000000-0000-0000-0000-000000000003', 'owner@example.com', 80, 60);
                    INSERT INTO ledger_member_cash_pool_share_deltas VALUES
                      (gen_random_uuid(), '00000000-0000-0000-0000-000000000002', 'owner@example.com', 100),
                      (gen_random_uuid(), '00000000-0000-0000-0000-000000000003', 'owner@example.com', -60);
                    INSERT INTO ledger_member_balance_transfers VALUES
                      (gen_random_uuid(), '00000000-0000-0000-0000-000000000003', 'owner@example.com', 'other@example.com', 20),
                      (gen_random_uuid(), '00000000-0000-0000-0000-000000000004', 'other@example.com', 'owner@example.com', 40);
                    """.trimIndent(),
                )
            }
        }
    }

    private fun legacyCounts(schema: String): List<Long> =
        dataSource.connection.use { connection ->
            connection.schema = schema
            connection.createStatement().use { statement ->
                statement
                    .executeQuery(
                        """
                        SELECT (SELECT count(*) FROM ledger_events),
                               (SELECT count(*) FROM ledger_cash_pool_income_events),
                               (SELECT count(*) FROM ledger_member_cash_pool_share_deltas),
                               (SELECT count(*) FROM ledger_member_balance_transfers)
                        """.trimIndent(),
                    ).use { rows ->
                        rows.next()
                        (1..4).map { rows.getLong(it) }
                    }
            }
        }

    private fun preservedLegacyData(schema: String): String =
        dataSource.connection.use { connection ->
            connection.schema = schema
            connection.createStatement().use { statement ->
                statement
                    .executeQuery(
                        """
                        SELECT jsonb_build_object(
                            'events', (SELECT jsonb_agg(to_jsonb(e) ORDER BY e.id) FROM ledger_events e),
                            'income', (SELECT jsonb_agg(to_jsonb(i) ORDER BY i.event) FROM ledger_cash_pool_income_events i),
                            'withdrawal', (SELECT jsonb_agg(jsonb_build_array(w.event, w.withdrawn_by, w.withdrawn_amount_in_cents)
                                                      ORDER BY w.event) FROM ledger_cash_pool_withdrawal_events w),
                            'allocations', (SELECT jsonb_agg(to_jsonb(d) ORDER BY d.id) FROM ledger_member_cash_pool_share_deltas d
                                            JOIN ledger_cash_pool_income_events i ON i.event = d.event),
                            'expense_transfers', (SELECT jsonb_agg(to_jsonb(t) ORDER BY t.id) FROM ledger_member_balance_transfers t
                                                  JOIN ledger_events e ON e.id = t.event WHERE e.type = 'ACCEPTED_EXPENSE')
                        )::text
                        """.trimIndent(),
                    ).use { rows ->
                        rows.next()
                        rows.getString(1)
                    }
            }
        }

    private fun legacyColumnCount(schema: String): Int =
        dataSource.connection.use { connection ->
            connection
                .prepareStatement(
                    "SELECT count(*) FROM information_schema.columns WHERE table_schema = ? " +
                        "AND table_name = 'ledger_cash_pool_withdrawal_events' AND column_name = 'own_revenue_share_consumed_in_cents'",
                ).use { statement ->
                    statement.setString(1, schema)
                    statement.executeQuery().use { rows ->
                        rows.next()
                        rows.getInt(1)
                    }
                }
        }

    private suspend fun persistedGroupFixture(seed: String): LedgerEventFixture {
        val owner = memberEmail("$seed-owner")
        val coOwner = memberEmail("$seed-co-owner")
        memberRepository.persist(Member(email = owner, createdAt = Instant.parse("2026-04-03T08:00:00Z")))
        memberRepository.persist(Member(email = coOwner, createdAt = Instant.parse("2026-04-03T08:01:00Z")))

        val group =
            Group
                .create(
                    id = groupId("$seed-group"),
                    createdBy = owner,
                    createdAt = Instant.parse("2026-04-03T08:02:00Z"),
                ).addMember(
                    member = coOwner,
                    joinedAt = Instant.parse("2026-04-03T08:03:00Z"),
                )
        groupRepository.persist(group)

        return LedgerEventFixture(
            seed = seed,
            group = group,
        )
    }

    private data class LedgerEventFixture(
        val seed: String,
        val group: Group,
    ) {
        val owner = group.createdBy
        val coOwner = group.members.single { member -> member.member != owner }.member

        fun revenueEvents(): List<LedgerEvent> =
            listOf(
                incomeEvent("income", "2026-04-03T10:00:00Z", 101),
            )

        fun incomeEvent(
            eventSeed: String,
            occurredAt: String,
            amountInCents: Long,
        ): CashPoolIncomeLedgerEvent =
            CashPoolIncomeLedgerEvent.from(
                id = ledgerEventId("$seed-$eventSeed"),
                group = group.id,
                occurredAt = Instant.parse(occurredAt),
                distribution =
                    RevenueDistribution.distribute(
                        totalAmount = MoneyAmount.ofCents(amountInCents),
                        ownershipShares =
                            setOf(
                                OwnershipShare(owner, OwnershipPercentage.ofBasisPoints(6000)),
                                OwnershipShare(coOwner, OwnershipPercentage.ofBasisPoints(4000)),
                            ),
                    ),
            )

        fun withdrawalEvent(): CashPoolWithdrawalLedgerEvent =
            CashPoolWithdrawalLedgerEvent(
                id = ledgerEventId("$seed-withdrawal"),
                group = group.id,
                withdrawnBy = owner,
                withdrawnAmount = MoneyAmount.ofCents(80),
                ownRevenueShareConsumed = MoneyAmount.ofCents(60),
                balanceTransfers =
                    setOf(
                        MemberBalanceTransfer(
                            fromMember = owner,
                            toMember = coOwner,
                            amount = MoneyAmount.ofCents(20),
                        ),
                    ),
                occurredAt = Instant.parse("2026-04-03T12:00:00Z"),
            )

        fun acceptedReimbursement(): Reimbursement =
            Reimbursement.recordDirect(
                id = ReimbursementId(tech.justdev.testsupport.testUuid("$seed-reimbursement")),
                group = group.id,
                paidBy = coOwner,
                receivedBy = owner,
                amount = MoneyAmount.ofCents(45),
                reimbursedAt = Instant.parse("2026-04-03T11:00:00Z"),
                declaredBy = owner,
                declaredAt = Instant.parse("2026-04-03T12:00:00Z"),
            )
    }
}
