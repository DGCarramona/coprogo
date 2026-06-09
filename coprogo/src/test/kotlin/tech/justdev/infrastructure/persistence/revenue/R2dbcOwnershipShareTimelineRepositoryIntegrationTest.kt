package tech.justdev.infrastructure.persistence.revenue

import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.domain.revenue.entity.OwnershipShareChange
import tech.justdev.domain.revenue.entity.OwnershipShareTimeline
import tech.justdev.domain.revenue.repository.OwnershipShareTimelineRepository
import tech.justdev.domain.revenue.valueobject.OwnershipPercentage
import tech.justdev.domain.revenue.valueobject.OwnershipShare
import tech.justdev.testsupport.PostgresMicronautTest
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import tech.justdev.testsupport.ownershipShareChangeId
import java.time.Instant
import java.time.LocalDate

@PostgresMicronautTest
class R2dbcOwnershipShareTimelineRepositoryIntegrationTest {
    @Inject
    lateinit var memberRepository: MemberRepository

    @Inject
    lateinit var groupRepository: GroupRepository

    @Inject
    lateinit var ownershipShareTimelineRepository: OwnershipShareTimelineRepository

    @Nested
    inner class Persist {
        @Test
        fun `should persist the full ownership share timeline history`() =
            runTest {
                val fixture = persistedGroupFixture("persist-full-history")
                val timeline = fixture.timeline()

                ownershipShareTimelineRepository.persist(timeline)

                assertEquals(timeline, ownershipShareTimelineRepository.findByGroup(fixture.group.id))
            }

        @Test
        fun `should append only missing changes and allocations when persisted twice`() =
            runTest {
                val fixture = persistedGroupFixture("persist-idempotent")
                val timeline = fixture.timeline()

                ownershipShareTimelineRepository.persist(timeline)
                ownershipShareTimelineRepository.persist(timeline)

                assertEquals(timeline, ownershipShareTimelineRepository.findByGroup(fixture.group.id))
            }
    }

    @Nested
    inner class FindByGroup {
        @Test
        fun `should return null when no timeline exists for the group`() =
            runTest {
                val fixture = persistedGroupFixture("find-missing")

                assertEquals(null, ownershipShareTimelineRepository.findByGroup(fixture.group.id))
            }

        @Test
        fun `should rebuild the persisted ownership share timeline history`() =
            runTest {
                val fixture = persistedGroupFixture("find-existing")
                val timeline = fixture.timeline()
                ownershipShareTimelineRepository.persist(timeline)

                assertEquals(timeline, ownershipShareTimelineRepository.findByGroup(fixture.group.id))
            }
    }

    private suspend fun persistedGroupFixture(seed: String): OwnershipShareTimelineFixture {
        val owner = memberEmail("ownership-repo-$seed-owner")
        val coOwner = memberEmail("ownership-repo-$seed-co-owner")
        memberRepository.persist(Member(email = owner, createdAt = Instant.parse("2026-04-13T10:00:00Z")))
        memberRepository.persist(Member(email = coOwner, createdAt = Instant.parse("2026-04-13T10:01:00Z")))

        val group =
            Group
                .create(
                    id = groupId("$seed-ownership-repo-group"),
                    createdBy = owner,
                    createdAt = Instant.parse("2026-04-13T10:02:00Z"),
                ).addMember(
                    member = coOwner,
                    joinedAt = Instant.parse("2026-04-13T10:03:00Z"),
                )
        groupRepository.persist(group)

        return OwnershipShareTimelineFixture(
            seed = seed,
            owner = owner,
            coOwner = coOwner,
            group = group,
        )
    }

    private data class OwnershipShareTimelineFixture(
        val seed: String,
        val owner: tech.justdev.domain.group.valueobject.MemberEmail,
        val coOwner: tech.justdev.domain.group.valueobject.MemberEmail,
        val group: Group,
    ) {
        fun timeline(): OwnershipShareTimeline =
            OwnershipShareTimeline(
                group = group.id,
                changes =
                    listOf(
                        OwnershipShareChange(
                            id = ownershipShareChangeId("a-$seed"),
                            effectiveDate = LocalDate.parse("2026-01-01"),
                            recordedBy = owner,
                            recordedAt = Instant.parse("2026-04-13T10:04:00Z"),
                            shares =
                                setOf(
                                    OwnershipShare(owner, OwnershipPercentage.ofBasisPoints(6000)),
                                    OwnershipShare(coOwner, OwnershipPercentage.ofBasisPoints(4000)),
                                ),
                        ),
                        OwnershipShareChange(
                            id = ownershipShareChangeId("b-$seed"),
                            effectiveDate = LocalDate.parse("2026-06-01"),
                            recordedBy = owner,
                            recordedAt = Instant.parse("2026-04-13T10:05:00Z"),
                            shares =
                                setOf(
                                    OwnershipShare(owner, OwnershipPercentage.ofBasisPoints(5000)),
                                    OwnershipShare(coOwner, OwnershipPercentage.ofBasisPoints(5000)),
                                ),
                        ),
                    ),
            )
    }
}
