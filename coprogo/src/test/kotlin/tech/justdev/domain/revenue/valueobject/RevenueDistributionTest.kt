package tech.justdev.domain.revenue.valueobject

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.testsupport.memberEmail

class RevenueDistributionTest {
    @Test
    fun `distribute should split cents with deterministic remainder allocation`() {
        val result =
            RevenueDistribution.distribute(
                totalAmount = MoneyAmount.ofCents(100),
                ownershipShares =
                    setOf(
                        OwnershipShare(member = memberEmail("alice"), percentage = OwnershipPercentage.ofBasisPoints(3333)),
                        OwnershipShare(member = memberEmail("bob"), percentage = OwnershipPercentage.ofBasisPoints(3333)),
                        OwnershipShare(member = memberEmail("carol"), percentage = OwnershipPercentage.ofBasisPoints(3334)),
                    ),
            )

        assertEquals(MoneyAmount.ofCents(100), result.totalAmount)
        assertEquals(
            setOf(
                RevenueAllocation(member = memberEmail("alice"), amount = MoneyAmount.ofCents(33)),
                RevenueAllocation(member = memberEmail("bob"), amount = MoneyAmount.ofCents(33)),
                RevenueAllocation(member = memberEmail("carol"), amount = MoneyAmount.ofCents(34)),
            ),
            result.allocations,
        )
    }

    @Test
    fun `distribute should break equal remainders deterministically by member id`() {
        val result =
            RevenueDistribution.distribute(
                totalAmount = MoneyAmount.ofCents(1),
                ownershipShares =
                    setOf(
                        OwnershipShare(member = memberEmail("bob"), percentage = OwnershipPercentage.ofBasisPoints(5000)),
                        OwnershipShare(member = memberEmail("alice"), percentage = OwnershipPercentage.ofBasisPoints(5000)),
                    ),
            )

        assertEquals(
            setOf(
                RevenueAllocation(member = memberEmail("alice"), amount = MoneyAmount.ofCents(1)),
                RevenueAllocation(member = memberEmail("bob"), amount = MoneyAmount.ZERO),
            ),
            result.allocations,
        )
    }

    @Test
    fun `distribute should fail when ownership shares do not sum to one hundred percent`() {
        assertThrows(IllegalArgumentException::class.java) {
            RevenueDistribution.distribute(
                totalAmount = MoneyAmount.ofCents(100),
                ownershipShares =
                    setOf(
                        OwnershipShare(member = memberEmail("alice"), percentage = OwnershipPercentage.ofBasisPoints(4000)),
                        OwnershipShare(member = memberEmail("bob"), percentage = OwnershipPercentage.ofBasisPoints(5000)),
                    ),
            )
        }
    }

    @Test
    fun `distribute should fail when the same member appears twice`() {
        assertThrows(IllegalArgumentException::class.java) {
            RevenueDistribution.distribute(
                totalAmount = MoneyAmount.ofCents(100),
                ownershipShares =
                    setOf(
                        OwnershipShare(member = memberEmail("alice"), percentage = OwnershipPercentage.ofBasisPoints(6000)),
                        OwnershipShare(member = memberEmail("alice"), percentage = OwnershipPercentage.ofBasisPoints(4000)),
                    ),
            )
        }
    }
}
