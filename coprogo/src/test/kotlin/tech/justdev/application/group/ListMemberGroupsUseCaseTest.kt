package tech.justdev.application.group

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.testsupport.groupId
import java.time.Instant

class ListMemberGroupsUseCaseTest {
    @Nested
    inner class Invoke {
        @Test
        fun `returns the summaries for the requesting member`() =
            runTest {
                val member = MemberEmail.of("alice@example.com")
                val groups = listOf(MemberGroupSnapshot(groupId("member-list").toPrimitive(), "bob@example.com", Instant.EPOCH))
                val projection = RecordingMemberGroupsProjection(groups)

                assertEquals(groups, ListMemberGroupsUseCaseImpl(projection)(ListMemberGroupsQuery(member)))
                assertEquals(listOf(member), projection.members)
            }

        @Test
        fun `returns no groups for a member without memberships`() =
            runTest {
                val projection = RecordingMemberGroupsProjection(emptyList())

                assertEquals(
                    emptyList<MemberGroupSnapshot>(),
                    ListMemberGroupsUseCaseImpl(projection)(ListMemberGroupsQuery(MemberEmail.of("new@example.com"))),
                )
            }
    }
}

private class RecordingMemberGroupsProjection(
    private val groups: List<MemberGroupSnapshot>,
) : MemberGroupsProjection {
    val members = mutableListOf<MemberEmail>()

    override suspend fun findByMember(member: MemberEmail): List<MemberGroupSnapshot> {
        members.add(member)
        return groups
    }
}
