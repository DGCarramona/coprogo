package tech.justdev.domain.revenue.valueobject

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import tech.justdev.domain.shared.money.MoneyAmount
import tech.justdev.testsupport.memberId

class RevenueDistributionTest {

    @Test
    fun `distribute should split cents with deterministic remainder allocation`() {
        val result = RevenueDistribution.distribute(
            totalAmount = MoneyAmount.ofCents(100),
            ownershipShares = setOf(
                OwnershipShare(memberId = memberId("alice"), percentage = OwnershipPercentage.ofBasisPoints(3333)),
                OwnershipShare(memberId = memberId("bob"), percentage = OwnershipPercentage.ofBasisPoints(3333)),
                OwnershipShare(memberId = memberId("carol"), percentage = OwnershipPercentage.ofBasisPoints(3334)),
            ),
        )

        assertEquals(MoneyAmount.ofCents(100), result.totalAmount)
        assertEquals(
            setOf(
                RevenueAllocation(memberId("alice"), MoneyAmount.ofCents(33)),
                RevenueAllocation(memberId("bob"), MoneyAmount.ofCents(33)),
                RevenueAllocation(memberId("carol"), MoneyAmount.ofCents(34)),
            ),
            result.allocations,
        )
    }

    @Test
    fun `distribute should break equal remainders deterministically by member id`() {
        val result = RevenueDistribution.distribute(
            totalAmount = MoneyAmount.ofCents(1),
            ownershipShares = setOf(
                OwnershipShare(memberId = memberId("bob"), percentage = OwnershipPercentage.ofBasisPoints(5000)),
                OwnershipShare(memberId = memberId("alice"), percentage = OwnershipPercentage.ofBasisPoints(5000)),
            ),
        )

        assertEquals(
            setOf(
                RevenueAllocation(memberId("alice"), MoneyAmount.ofCents(1)),
                RevenueAllocation(memberId("bob"), MoneyAmount.ZERO),
            ),
            result.allocations,
        )
    }

    @Test
    fun `distribute should fail when ownership shares do not sum to one hundred percent`() {
        assertThrows(IllegalArgumentException::class.java) {
            RevenueDistribution.distribute(
                totalAmount = MoneyAmount.ofCents(100),
                ownershipShares = setOf(
                    OwnershipShare(memberId = memberId("alice"), percentage = OwnershipPercentage.ofBasisPoints(4000)),
                    OwnershipShare(memberId = memberId("bob"), percentage = OwnershipPercentage.ofBasisPoints(5000)),
                ),
            )
        }
    }

    @Test
    fun `distribute should fail when the same member appears twice`() {
        assertThrows(IllegalArgumentException::class.java) {
            RevenueDistribution.distribute(
                totalAmount = MoneyAmount.ofCents(100),
                ownershipShares = setOf(
                    OwnershipShare(memberId = memberId("alice"), percentage = OwnershipPercentage.ofBasisPoints(6000)),
                    OwnershipShare(memberId = memberId("alice"), percentage = OwnershipPercentage.ofBasisPoints(4000)),
                ),
            )
        }
    }
}
