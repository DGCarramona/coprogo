package tech.justdev.domain.ledger.event

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.ledger.effect.MemberBalanceTransfer
import tech.justdev.domain.ledger.projection.MemberCashPoolShareBalance
import tech.justdev.domain.ledger.valueobject.NetBalanceAmount
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.ledgerEventId
import tech.justdev.testsupport.memberEmail
import java.time.Instant
import java.util.stream.Stream

class CashPoolWithdrawalFactoryTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("withdrawalScenarios")
    fun `from should consume own share then compensate other positive share holders`(scenario: WithdrawalScenario) {
        assertEquals(
            scenario.expected,
            CashPoolWithdrawalLedgerEvent.from(
                id = ledgerEventId("withdrawal"),
                group = groupId("group"),
                withdrawnBy = alice,
                withdrawnAmount = scenario.withdrawnAmount,
                occurredAt = occurredAt,
                memberCashPoolShares = scenario.memberCashPoolShares,
            ),
        )
    }

    data class WithdrawalScenario(
        val name: String,
        val withdrawnAmount: MoneyAmount,
        val memberCashPoolShares: Set<MemberCashPoolShareBalance>,
        val expected: CashPoolWithdrawalLedgerEvent,
    ) {
        override fun toString(): String = name
    }

    private companion object {
        val alice = memberEmail("alice")
        val bob = memberEmail("bob")
        val carol = memberEmail("carol")
        val diana = memberEmail("diana")
        val occurredAt: Instant = Instant.parse("2026-04-03T12:00:00Z")

        @JvmStatic
        fun withdrawalScenarios(): Stream<WithdrawalScenario> =
            Stream.of(
                WithdrawalScenario(
                    name = "own share alone",
                    withdrawnAmount = MoneyAmount.ofCents(60),
                    memberCashPoolShares = setOf(share(alice, 100)),
                    expected = event(withdrawnAmount = 60, ownShareConsumed = 60),
                ),
                WithdrawalScenario(
                    name = "over-withdrawal allocates cents by largest remainders",
                    withdrawnAmount = MoneyAmount.ofCents(13),
                    memberCashPoolShares = setOf(share(alice, 10), share(bob, 1), share(carol, 3)),
                    expected =
                        event(
                            withdrawnAmount = 13,
                            ownShareConsumed = 10,
                            transfers = setOf(transfer(bob, 1), transfer(carol, 2)),
                        ),
                ),
                WithdrawalScenario(
                    name = "equal remainders allocate cents by stable email order",
                    withdrawnAmount = MoneyAmount.ofCents(3),
                    memberCashPoolShares = setOf(share(alice, 1), share(bob, 1), share(carol, 1), share(diana, 1)),
                    expected =
                        event(
                            withdrawnAmount = 3,
                            ownShareConsumed = 1,
                            transfers = setOf(transfer(bob, 1), transfer(carol, 1)),
                        ),
                ),
            )

        private fun share(
            member: MemberEmail,
            cents: Long,
        ): MemberCashPoolShareBalance = MemberCashPoolShareBalance(member = member, amount = NetBalanceAmount.ofCents(cents))

        private fun event(
            withdrawnAmount: Long,
            ownShareConsumed: Long,
            transfers: Set<MemberBalanceTransfer> = emptySet(),
        ): CashPoolWithdrawalLedgerEvent =
            CashPoolWithdrawalLedgerEvent(
                id = ledgerEventId("withdrawal"),
                group = groupId("group"),
                withdrawnBy = alice,
                withdrawnAmount = MoneyAmount.ofCents(withdrawnAmount),
                ownRevenueShareConsumed = MoneyAmount.ofCents(ownShareConsumed),
                balanceTransfers = transfers,
                occurredAt = occurredAt,
            )

        private fun transfer(
            toMember: MemberEmail,
            cents: Long,
        ): MemberBalanceTransfer = MemberBalanceTransfer(fromMember = alice, toMember = toMember, amount = MoneyAmount.ofCents(cents))
    }
}
