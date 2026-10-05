package tech.justdev.domain.revenue.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import tech.justdev.domain.revenue.valueobject.OwnershipPercentage
import tech.justdev.domain.revenue.valueobject.OwnershipShare
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.ownershipShareChangeId
import java.time.Instant
import java.time.LocalDate

class OwnershipShareTimelineTest {
    @Test
    fun `sharesAt should return the latest shares effective on the requested date`() {
        val timeline =
            OwnershipShareTimeline
                .empty(groupId("group-1"))
                .recordChange(
                    change(
                        changeId = "change-1",
                        effectiveDate = LocalDate.parse("2026-01-01"),
                        shares =
                            setOf(
                                share("alice", 6000),
                                share("bob", 4000),
                            ),
                    ),
                ).recordChange(
                    change(
                        changeId = "change-2",
                        effectiveDate = LocalDate.parse("2026-03-01"),
                        shares =
                            setOf(
                                share("alice", 5000),
                                share("bob", 3000),
                                share("carol", 2000),
                            ),
                    ),
                )

        assertEquals(
            setOf(
                share("alice", 5000),
                share("bob", 3000),
                share("carol", 2000),
            ),
            timeline.sharesAt(LocalDate.parse("2026-03-15")),
        )
        assertEquals(
            setOf(
                share("alice", 6000),
                share("bob", 4000),
            ),
            timeline.sharesAt(LocalDate.parse("2026-02-15")),
        )
    }

    @Test
    fun `sharesAt should apply a change on its effective date`() {
        val effectiveDate = LocalDate.parse("2026-03-01")
        val effectiveShares = setOf(share("alice", 5000), share("bob", 5000))
        val timeline =
            OwnershipShareTimeline(
                group = groupId("effective-date-group"),
                changes = listOf(change("effective-date-change", effectiveDate, effectiveShares)),
            )

        assertEquals(effectiveShares, timeline.sharesAt(effectiveDate))
    }

    @Test
    fun `timeline should reject duplicate change ids`() {
        val duplicateId = "duplicate-change"

        assertThrows(IllegalArgumentException::class.java) {
            OwnershipShareTimeline(
                group = groupId("duplicate-change-group"),
                changes =
                    listOf(
                        change(duplicateId, LocalDate.parse("2026-01-01"), setOf(share("alice", 10000))),
                        change(duplicateId, LocalDate.parse("2026-02-01"), setOf(share("alice", 10000))),
                    ),
            )
        }
    }

    @Test
    fun `ownership share change should reject duplicate members`() {
        assertThrows(IllegalArgumentException::class.java) {
            change(
                changeId = "duplicate-member-change",
                effectiveDate = LocalDate.parse("2026-01-01"),
                shares =
                    setOf(
                        share("alice", 6000),
                        share("alice", 4000),
                    ),
            )
        }
    }

    @Test
    fun `ownership share change should reject a total other than one hundred percent`() {
        assertThrows(IllegalArgumentException::class.java) {
            change(
                changeId = "invalid-total-change",
                effectiveDate = LocalDate.parse("2026-01-01"),
                shares = setOf(share("alice", 6000), share("bob", 3000)),
            )
        }
    }

    @Test
    fun `recordChange should reject another change with the same effective date`() {
        val timeline =
            OwnershipShareTimeline
                .empty(groupId("group-1"))
                .recordChange(
                    change(
                        changeId = "change-1",
                        effectiveDate = LocalDate.parse("2026-01-01"),
                        shares =
                            setOf(
                                share("alice", 6000),
                                share("bob", 4000),
                            ),
                    ),
                )

        assertThrows(IllegalArgumentException::class.java) {
            timeline.recordChange(
                change(
                    changeId = "change-2",
                    effectiveDate = LocalDate.parse("2026-01-01"),
                    shares =
                        setOf(
                            share("alice", 5000),
                            share("bob", 5000),
                        ),
                ),
            )
        }
    }

    @Test
    fun `sharesAt should fail when no change is effective yet`() {
        val timeline = OwnershipShareTimeline.empty(groupId("group-1"))

        assertThrows(IllegalArgumentException::class.java) {
            timeline.sharesAt(LocalDate.parse("2026-01-01"))
        }
    }

    private fun share(
        member: String,
        basisPoints: Int,
    ) = OwnershipShare(
        member = memberEmail(member),
        percentage = OwnershipPercentage.ofBasisPoints(basisPoints),
    )

    private fun change(
        changeId: String,
        effectiveDate: LocalDate,
        shares: Set<OwnershipShare>,
    ) = OwnershipShareChange(
        id = ownershipShareChangeId(changeId),
        effectiveDate = effectiveDate,
        recordedBy = memberEmail("owner"),
        recordedAt = Instant.parse("2026-04-03T10:00:00Z"),
        shares = shares,
    )
}
