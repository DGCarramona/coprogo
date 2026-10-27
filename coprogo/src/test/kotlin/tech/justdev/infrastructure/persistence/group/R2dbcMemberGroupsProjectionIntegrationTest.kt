package tech.justdev.infrastructure.persistence.group

import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tech.justdev.application.group.MemberGroupSnapshot
import tech.justdev.application.group.MemberGroupsProjection
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.domain.group.valueobject.GroupName
import tech.justdev.testsupport.PostgresMicronautTest
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import java.time.Instant

@PostgresMicronautTest
class R2dbcMemberGroupsProjectionIntegrationTest {
    @Inject lateinit var projection: MemberGroupsProjection

    @Inject lateinit var groups: GroupRepository

    @Inject lateinit var members: MemberRepository

    @Nested
    inner class FindByMember {
        @Test
        fun `lists created and joined groups once in chronological and identifier order`() =
            runTest {
                val alice = memberEmail("list-groups-alice")
                val bob = memberEmail("list-groups-bob")
                val earlier = Instant.parse("2026-01-01T00:00:00Z")
                val later = Instant.parse("2026-02-01T00:00:00Z")
                members.persist(Member(alice, earlier))
                members.persist(Member(bob, earlier))
                val created =
                    Group
                        .create(
                            groupId("list-created"),
                            alice,
                            later,
                            GroupName.of("Appartement familial"),
                        ).addMember(bob, later)
                val joined = Group.create(groupId("list-joined"), bob, earlier).addMember(alice, later)
                val sameDate = Group.create(groupId("list-same-date"), alice, later)
                groups.persist(created)
                groups.persist(Group.create(groupId("list-unrelated"), bob, earlier))
                groups.persist(sameDate)
                groups.persist(joined)

                val expected =
                    listOf(joined, created, sameDate)
                        .sortedWith(compareBy({ it.createdAt }, { it.id.toPrimitive().toString() }))
                        .map { MemberGroupSnapshot(it.id.toPrimitive(), it.createdBy.toPrimitive(), it.createdAt, it.name?.toPrimitive()) }

                assertEquals(expected, projection.findByMember(alice))
            }

        @Test
        fun `returns no groups for an unknown member`() =
            runTest {
                assertEquals(emptyList<MemberGroupSnapshot>(), projection.findByMember(memberEmail("no-memberships")))
            }
    }
}
