package tech.justdev.domain.ledger.projection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.ledger.effect.MemberBalanceTransfer
import tech.justdev.domain.ledger.effect.MemberCashPoolShareDelta
import tech.justdev.domain.ledger.event.AcceptedExpenseLedgerEvent
import tech.justdev.domain.ledger.event.CashPoolIncomeLedgerEvent
import tech.justdev.domain.ledger.event.CashPoolWithdrawalLedgerEvent
import tech.justdev.domain.ledger.event.LedgerEvent
import tech.justdev.domain.ledger.valueobject.LedgerEventId
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.testsupport.expenseId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import java.time.Instant
import java.util.UUID

class CashPoolChronologyTest {
    @Nested
    inner class ReplayCashPoolChronologically {
        @Test
        fun `replays reversed entries and recomputes withdrawals from the remaining own and other shares`() {
            val firstIncome = income(1, "2026-04-01T10:00:00Z", alice to 50, bob to 50)
            val firstWithdrawal = withdrawal(2, "2026-04-01T11:00:00Z", alice, 70)
            val secondIncome = income(3, "2026-04-02T10:00:00Z", alice to 50, bob to 50)
            val secondWithdrawal = withdrawal(4, "2026-04-02T11:00:00Z", bob, 90)
            val original = listOf(secondWithdrawal, secondIncome, firstWithdrawal, firstIncome)

            val replayed = original.replayCashPoolChronologically()

            assertEquals(
                listOf(
                    firstIncome,
                    firstWithdrawal.copy(ownRevenueShareConsumed = money(50), balanceTransfers = setOf(transfer(alice, bob, 20))),
                    secondIncome,
                    secondWithdrawal.copy(ownRevenueShareConsumed = money(80), balanceTransfers = setOf(transfer(bob, alice, 10))),
                ),
                replayed,
            )
            assertEquals(listOf(secondWithdrawal, secondIncome, firstWithdrawal, firstIncome), original)
            assertSame(firstIncome, replayed[0])
            assertSame(secondIncome, replayed[2])
        }

        @Test
        fun `same UTC day income comes first even when recorded later`() {
            val previousDayIncome = income(1, "2026-04-01T23:59:59Z", alice to 20)
            val earlyWithdrawal = withdrawal(2, "2026-04-02T00:00:00Z", alice, 70)
            val laterIncome = income(3, "2026-04-02T23:59:59Z", bob to 50)

            assertEquals(
                listOf(
                    previousDayIncome,
                    laterIncome,
                    earlyWithdrawal.copy(ownRevenueShareConsumed = money(20), balanceTransfers = setOf(transfer(alice, bob, 50))),
                ),
                listOf(earlyWithdrawal, laterIncome, previousDayIncome).replayCashPoolChronologically(),
            )
        }

        @Test
        fun `orders incomes and other events by instant then UUID independently of input order`() {
            val incomeLaterId = income(3, "2026-04-01T20:00:00Z", alice to 10)
            val incomeEarlierId = income(1, "2026-04-01T20:00:00Z", alice to 10)
            val expenseEarlier = expense(8, "2026-04-01T01:00:00Z")
            val expenseEarlierId = expense(2, "2026-04-01T02:00:00Z")
            val expenseLaterId = expense(4, "2026-04-01T02:00:00Z")
            val expected = listOf(incomeEarlierId, incomeLaterId, expenseEarlier, expenseEarlierId, expenseLaterId)

            assertEquals(expected, expected.reversed().replayCashPoolChronologically())
            assertEquals(
                expected,
                listOf(expenseLaterId, incomeLaterId, expenseEarlier, incomeEarlierId, expenseEarlierId)
                    .replayCashPoolChronologically(),
            )
            assertSame(expenseEarlier, expected.reversed().replayCashPoolChronologically()[2])
        }

        @Test
        fun `rejects withdrawal when later days would be required to fund it`() {
            val firstIncome = income(1, "2026-04-01T10:00:00Z", alice to 40)
            val firstWithdrawal = withdrawal(2, "2026-04-01T11:00:00Z", alice, 30)
            val insufficientWithdrawal = withdrawal(3, "2026-04-02T10:00:00Z", bob, 20)
            val laterIncome = income(4, "2026-04-03T10:00:00Z", bob to 100)

            val error =
                assertThrows(IllegalArgumentException::class.java) {
                    listOf(laterIncome, insufficientWithdrawal, firstWithdrawal, firstIncome).replayCashPoolChronologically()
                }

            assertTrue(error.message!!.contains("insufficient", ignoreCase = true))
            assertTrue(error.message!!.contains("20"))
            assertTrue(error.message!!.contains("10"))
        }

        @Test
        fun `does not use another group's money or shares`() {
            val incomeOtherGroup = income(1, "2026-04-01T10:00:00Z", bob to 100, group = otherGroup)
            val withdrawal = withdrawal(2, "2026-04-01T11:00:00Z", alice, 20)

            assertThrows(IllegalArgumentException::class.java) {
                listOf(incomeOtherGroup, withdrawal).replayCashPoolChronologically()
            }
        }

        @Test
        fun `returns an empty list for an empty history`() {
            assertEquals(emptyList<LedgerEvent>(), emptyList<LedgerEvent>().replayCashPoolChronologically())
        }
    }

    private companion object {
        val group: GroupId = groupId("group")
        val otherGroup = groupId("other")
        val alice = memberEmail("alice")
        val bob = memberEmail("bob")

        fun id(number: Long): LedgerEventId = LedgerEventId(UUID(0, number))

        fun money(cents: Long): MoneyAmount = MoneyAmount.ofCents(cents)

        fun income(
            number: Long,
            occurredAt: String,
            vararg allocations: Pair<MemberEmail, Long>,
            group: GroupId = this.group,
        ): CashPoolIncomeLedgerEvent =
            CashPoolIncomeLedgerEvent(
                id = id(number),
                group = group,
                amount = money(allocations.sumOf { it.second }),
                allocations = allocations.map { (member, cents) -> MemberCashPoolShareDelta.increase(member, money(cents)) }.toSet(),
                occurredAt = Instant.parse(occurredAt),
            )

        fun withdrawal(
            number: Long,
            occurredAt: String,
            member: MemberEmail,
            cents: Long,
        ): CashPoolWithdrawalLedgerEvent =
            CashPoolWithdrawalLedgerEvent(
                id = id(number),
                group = group,
                withdrawnBy = member,
                withdrawnAmount = money(cents),
                ownRevenueShareConsumed = money(cents),
                balanceTransfers = emptySet(),
                occurredAt = Instant.parse(occurredAt),
            )

        fun expense(
            number: Long,
            occurredAt: String,
        ): AcceptedExpenseLedgerEvent =
            AcceptedExpenseLedgerEvent(
                id = id(number),
                group = group,
                expense = expenseId("expense-$number"),
                paidBy = alice,
                occurredAt = Instant.parse(occurredAt),
                transfers = setOf(transfer(bob, alice, 1)),
            )

        fun transfer(
            from: MemberEmail,
            to: MemberEmail,
            cents: Long,
        ): MemberBalanceTransfer = MemberBalanceTransfer(fromMember = from, toMember = to, amount = money(cents))
    }
}
