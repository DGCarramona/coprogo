package tech.justdev.domain.expense.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tech.justdev.domain.expense.valueobject.ExpenseParticipation
import tech.justdev.domain.expense.valueobject.ExpenseParticipationDecision
import tech.justdev.domain.expense.valueobject.ExpenseParticipationStatus
import tech.justdev.domain.expense.valueobject.ExpenseShare
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.testsupport.expenseId
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import java.time.Instant

class ExpenseTest {
    @Test
    fun `proposeEqualSplit should split remainder deterministically by member id`() {
        assertEquals(
            Expense(
                id = expenseId("expense-1"),
                group = groupId("group-1"),
                title = "Boiler repair",
                createdBy = memberEmail("alice"),
                totalAmount = MoneyAmount.ofCents(100),
                createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                participations =
                    setOf(
                        ExpenseParticipation(
                            memberEmail("alice"),
                            MoneyAmount.ofCents(34),
                            ExpenseParticipationStatus.Approved(Instant.parse("2026-04-03T10:00:00Z")),
                        ),
                        ExpenseParticipation(memberEmail("bob"), MoneyAmount.ofCents(33), ExpenseParticipationStatus.Pending),
                        ExpenseParticipation(memberEmail("carol"), MoneyAmount.ofCents(33), ExpenseParticipationStatus.Pending),
                    ),
            ),
            Expense.proposeEqualSplit(
                id = expenseId("expense-1"),
                group = groupId("group-1"),
                title = "Boiler repair",
                createdBy = memberEmail("alice"),
                totalAmount = MoneyAmount.ofCents(100),
                createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                participants = setOf(memberEmail("carol"), memberEmail("alice"), memberEmail("bob")),
            ),
        )
    }

    @Test
    fun `proposeEqualSplit should fail when at least one participant would receive zero`() {
        assertThrows(IllegalArgumentException::class.java) {
            Expense.proposeEqualSplit(
                id = expenseId("expense-1"),
                group = groupId("group-1"),
                title = "Boiler repair",
                createdBy = memberEmail("alice"),
                totalAmount = MoneyAmount.ofCents(2),
                createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                participants = setOf(memberEmail("alice"), memberEmail("bob"), memberEmail("carol")),
            )
        }
    }

    @Nested
    inner class ProposeEqualSplitWithCaps {
        @Test
        fun `should redistribute a capped share equally`() {
            val expense =
                proposeEqualSplitWithCaps(
                    totalAmountInCents = 100,
                    participants = setOf("alice", "bob", "carol"),
                    caps = mapOf("bob" to 20),
                )

            assertEquals(
                mapOf(
                    memberEmail("alice") to MoneyAmount.ofCents(40),
                    memberEmail("bob") to MoneyAmount.ofCents(20),
                    memberEmail("carol") to MoneyAmount.ofCents(40),
                ),
                expense.amountsByMember(),
            )
        }

        @Test
        fun `should redistribute iteratively when another cap becomes active`() {
            val expense =
                proposeEqualSplitWithCaps(
                    totalAmountInCents = 100,
                    participants = setOf("dave", "carol", "bob", "alice"),
                    caps = mapOf("bob" to 10, "carol" to 28),
                )

            assertEquals(
                mapOf(
                    memberEmail("alice") to MoneyAmount.ofCents(31),
                    memberEmail("bob") to MoneyAmount.ofCents(10),
                    memberEmail("carol") to MoneyAmount.ofCents(28),
                    memberEmail("dave") to MoneyAmount.ofCents(31),
                ),
                expense.amountsByMember(),
            )
        }

        @Test
        fun `should assign the remaining cent deterministically by member email`() {
            val expense =
                proposeEqualSplitWithCaps(
                    totalAmountInCents = 101,
                    participants = setOf("carol", "bob", "alice"),
                    caps = mapOf("bob" to 20),
                )

            assertEquals(
                mapOf(
                    memberEmail("alice") to MoneyAmount.ofCents(41),
                    memberEmail("bob") to MoneyAmount.ofCents(20),
                    memberEmail("carol") to MoneyAmount.ofCents(40),
                ),
                expense.amountsByMember(),
            )
        }

        @Test
        fun `should leave equal shares unchanged when a cap is above them`() {
            val expense =
                proposeEqualSplitWithCaps(
                    totalAmountInCents = 100,
                    participants = setOf("carol", "bob", "alice"),
                    caps = mapOf("bob" to 40),
                )

            assertEquals(
                mapOf(
                    memberEmail("alice") to MoneyAmount.ofCents(34),
                    memberEmail("bob") to MoneyAmount.ofCents(33),
                    memberEmail("carol") to MoneyAmount.ofCents(33),
                ),
                expense.amountsByMember(),
            )
        }

        @Test
        fun `should reject when every participant has a cap`() {
            assertThrows(IllegalArgumentException::class.java) {
                proposeEqualSplitWithCaps(
                    totalAmountInCents = 100,
                    participants = setOf("alice", "bob"),
                    caps = mapOf("alice" to 40, "bob" to 60),
                )
            }
        }

        @Test
        fun `should reject a cap for a non participant`() {
            assertThrows(IllegalArgumentException::class.java) {
                proposeEqualSplitWithCaps(
                    totalAmountInCents = 100,
                    participants = setOf("alice", "bob"),
                    caps = mapOf("carol" to 20),
                )
            }
        }

        @Test
        fun `should reject a zero cap`() {
            assertThrows(IllegalArgumentException::class.java) {
                proposeEqualSplitWithCaps(
                    totalAmountInCents = 100,
                    participants = setOf("alice", "bob"),
                    caps = mapOf("bob" to 0),
                )
            }
        }
    }

    @Nested
    inner class ProposeCumulativeTiers {
        @Test
        fun `should add member contributions across successive tiers`() {
            val expense =
                proposeCumulativeTiers(
                    totalAmountInCents = 100,
                    tiers =
                        listOf(
                            CumulativeExpenseTier(
                                upTo = MoneyAmount.ofCents(60),
                                participants = setOf(memberEmail("carol"), memberEmail("alice"), memberEmail("bob")),
                            ),
                            CumulativeExpenseTier(
                                upTo = MoneyAmount.ofCents(80),
                                participants = setOf(memberEmail("bob"), memberEmail("alice")),
                            ),
                            CumulativeExpenseTier(
                                upTo = MoneyAmount.ofCents(100),
                                participants = setOf(memberEmail("alice")),
                            ),
                        ),
                )

            assertEquals(
                mapOf(
                    memberEmail("alice") to MoneyAmount.ofCents(50),
                    memberEmail("bob") to MoneyAmount.ofCents(30),
                    memberEmail("carol") to MoneyAmount.ofCents(20),
                ),
                expense.amountsByMember(),
            )
        }

        @Test
        fun `should assign remaining cents by member email in every tier`() {
            val expense =
                proposeCumulativeTiers(
                    totalAmountInCents = 101,
                    tiers =
                        listOf(
                            CumulativeExpenseTier(
                                upTo = MoneyAmount.ofCents(50),
                                participants = setOf(memberEmail("carol"), memberEmail("bob"), memberEmail("alice")),
                            ),
                            CumulativeExpenseTier(
                                upTo = MoneyAmount.ofCents(101),
                                participants = setOf(memberEmail("bob"), memberEmail("alice")),
                            ),
                        ),
                )

            assertEquals(
                mapOf(
                    memberEmail("alice") to MoneyAmount.ofCents(43),
                    memberEmail("bob") to MoneyAmount.ofCents(42),
                    memberEmail("carol") to MoneyAmount.ofCents(16),
                ),
                expense.amountsByMember(),
            )
        }

        @Test
        fun `should reject an empty tier list`() {
            assertThrows(IllegalArgumentException::class.java) {
                proposeCumulativeTiers(totalAmountInCents = 100, tiers = emptyList())
            }
        }

        @Test
        fun `should reject non increasing cumulative bounds`() {
            assertThrows(IllegalArgumentException::class.java) {
                proposeCumulativeTiers(
                    totalAmountInCents = 100,
                    tiers =
                        listOf(
                            CumulativeExpenseTier(MoneyAmount.ofCents(60), setOf(memberEmail("alice"))),
                            CumulativeExpenseTier(MoneyAmount.ofCents(60), setOf(memberEmail("alice"))),
                            CumulativeExpenseTier(MoneyAmount.ofCents(100), setOf(memberEmail("alice"))),
                        ),
                )
            }
        }

        @Test
        fun `should reject when the last bound differs from the total amount`() {
            assertThrows(IllegalArgumentException::class.java) {
                proposeCumulativeTiers(
                    totalAmountInCents = 100,
                    tiers =
                        listOf(
                            CumulativeExpenseTier(MoneyAmount.ofCents(90), setOf(memberEmail("alice"))),
                        ),
                )
            }
        }

        @Test
        fun `should reject a tier without participants`() {
            assertThrows(IllegalArgumentException::class.java) {
                CumulativeExpenseTier(
                    upTo = MoneyAmount.ofCents(100),
                    participants = emptySet(),
                )
            }
        }

        @Test
        fun `should reject a zero cumulative bound`() {
            assertThrows(IllegalArgumentException::class.java) {
                CumulativeExpenseTier(
                    upTo = MoneyAmount.ZERO,
                    participants = setOf(memberEmail("alice")),
                )
            }
        }

        @Test
        fun `should reject a tier that would allocate zero to a participant`() {
            assertThrows(IllegalArgumentException::class.java) {
                proposeCumulativeTiers(
                    totalAmountInCents = 2,
                    tiers =
                        listOf(
                            CumulativeExpenseTier(
                                upTo = MoneyAmount.ofCents(2),
                                participants = setOf(memberEmail("alice"), memberEmail("bob"), memberEmail("carol")),
                            ),
                        ),
                )
            }
        }
    }

    @Nested
    inner class Propose {
        @Test
        fun `should auto approve creator participation and wait for other members`() {
            assertEquals(
                proposedExpense(),
                Expense.propose(
                    id = expenseId("expense-1"),
                    group = groupId("group-1"),
                    title = "Plumber invoice",
                    createdBy = memberEmail("alice"),
                    totalAmount = MoneyAmount.ofCents(100),
                    createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                    shares =
                        setOf(
                            ExpenseShare(memberEmail("alice"), MoneyAmount.ofCents(40)),
                            ExpenseShare(memberEmail("bob"), MoneyAmount.ofCents(60)),
                        ),
                ),
            )
        }

        @Test
        fun `should fail when creator does not participate`() {
            assertThrows(IllegalArgumentException::class.java) {
                Expense.propose(
                    id = expenseId("expense-1"),
                    group = groupId("group-1"),
                    title = "Plumber invoice",
                    createdBy = memberEmail("alice"),
                    totalAmount = MoneyAmount.ofCents(100),
                    createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                    shares =
                        setOf(
                            ExpenseShare(memberEmail("bob"), MoneyAmount.ofCents(100)),
                        ),
                )
            }
        }
    }

    @Test
    fun `recordParticipationDecision should accept expense when last pending member approves`() {
        val proposedExpense = proposedExpense()

        assertEquals(
            Expense(
                id = expenseId("expense-1"),
                group = groupId("group-1"),
                title = "Plumber invoice",
                createdBy = memberEmail("alice"),
                totalAmount = MoneyAmount.ofCents(100),
                createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                participations =
                    setOf(
                        ExpenseParticipation(
                            memberEmail("alice"),
                            MoneyAmount.ofCents(40),
                            ExpenseParticipationStatus.Approved(Instant.parse("2026-04-03T10:00:00Z")),
                        ),
                        ExpenseParticipation(
                            memberEmail("bob"),
                            MoneyAmount.ofCents(60),
                            ExpenseParticipationStatus.Approved(Instant.parse("2026-04-03T12:00:00Z")),
                        ),
                    ),
            ),
            proposedExpense.recordParticipationDecision(
                member = memberEmail("bob"),
                decision = ExpenseParticipationDecision.APPROVE,
                decidedAt = Instant.parse("2026-04-03T12:00:00Z"),
            ),
        )
    }

    @Test
    fun `recordParticipationDecision should invalidate expense when a member refuses`() {
        val proposedExpense = proposedExpense()

        assertEquals(
            Expense(
                id = expenseId("expense-1"),
                group = groupId("group-1"),
                title = "Plumber invoice",
                createdBy = memberEmail("alice"),
                totalAmount = MoneyAmount.ofCents(100),
                createdAt = Instant.parse("2026-04-03T10:00:00Z"),
                participations =
                    setOf(
                        ExpenseParticipation(
                            memberEmail("alice"),
                            MoneyAmount.ofCents(40),
                            ExpenseParticipationStatus.Approved(Instant.parse("2026-04-03T10:00:00Z")),
                        ),
                        ExpenseParticipation(
                            memberEmail("bob"),
                            MoneyAmount.ofCents(60),
                            ExpenseParticipationStatus.Refused(Instant.parse("2026-04-03T12:00:00Z")),
                        ),
                    ),
            ),
            proposedExpense.recordParticipationDecision(
                member = memberEmail("bob"),
                decision = ExpenseParticipationDecision.REFUSE,
                decidedAt = Instant.parse("2026-04-03T12:00:00Z"),
            ),
        )
    }

    private fun proposedExpense(): Expense =
        Expense.propose(
            id = expenseId("expense-1"),
            group = groupId("group-1"),
            title = "Plumber invoice",
            createdBy = memberEmail("alice"),
            totalAmount = MoneyAmount.ofCents(100),
            createdAt = Instant.parse("2026-04-03T10:00:00Z"),
            shares =
                setOf(
                    ExpenseShare(memberEmail("alice"), MoneyAmount.ofCents(40)),
                    ExpenseShare(memberEmail("bob"), MoneyAmount.ofCents(60)),
                ),
        )

    private fun proposeEqualSplitWithCaps(
        totalAmountInCents: Long,
        participants: Set<String>,
        caps: Map<String, Long>,
    ): Expense =
        Expense.proposeEqualSplitWithCaps(
            id = expenseId("expense-with-caps"),
            group = groupId("group-1"),
            title = "Boiler repair",
            createdBy = memberEmail("alice"),
            totalAmount = MoneyAmount.ofCents(totalAmountInCents),
            createdAt = Instant.parse("2026-04-03T10:00:00Z"),
            participants = participants.map(::memberEmail).toSet(),
            capsByMember = caps.mapKeys { (member) -> memberEmail(member) }.mapValues { (_, amount) -> MoneyAmount.ofCents(amount) },
        )

    private fun proposeCumulativeTiers(
        totalAmountInCents: Long,
        tiers: List<CumulativeExpenseTier>,
    ): Expense =
        Expense.proposeCumulativeTiers(
            id = expenseId("expense-with-cumulative-tiers"),
            group = groupId("group-1"),
            title = "Boiler repair",
            createdBy = memberEmail("alice"),
            totalAmount = MoneyAmount.ofCents(totalAmountInCents),
            createdAt = Instant.parse("2026-04-03T10:00:00Z"),
            tiers = tiers,
        )

    private fun Expense.amountsByMember() = participations.associate { participation -> participation.member to participation.amount }
}
