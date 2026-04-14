package tech.justdev.domain.group.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.memberEmail
import java.time.Instant

class GroupTest {
    @Test
    fun `create should add the creator as the initial group member`() {
        val createdAt = Instant.parse("2026-04-13T10:15:30Z")

        val group =
            Group.create(
                id = groupId("group-1"),
                createdBy = memberEmail("alice"),
                createdAt = createdAt,
            )

        assertEquals(
            setOf(GroupMember(member = memberEmail("alice"), joinedAt = createdAt)),
            group.members,
        )
    }

    @Test
    fun `addMember should add a new group member`() {
        val createdAt = Instant.parse("2026-04-13T10:15:30Z")
        val joinedAt = Instant.parse("2026-04-14T08:00:00Z")
        val group = Group.create(groupId("group-1"), memberEmail("alice"), createdAt)

        val updatedGroup = group.addMember(member = memberEmail("bob"), joinedAt = joinedAt)

        assertEquals(
            setOf(
                GroupMember(member = memberEmail("alice"), joinedAt = createdAt),
                GroupMember(member = memberEmail("bob"), joinedAt = joinedAt),
            ),
            updatedGroup.members,
        )
    }

    @Test
    fun `addMember should fail when the member is already in the group`() {
        val group = Group.create(groupId("group-1"), memberEmail("alice"), Instant.parse("2026-04-13T10:15:30Z"))

        assertThrows<IllegalArgumentException> {
            group.addMember(member = memberEmail("alice"), joinedAt = Instant.parse("2026-04-14T08:00:00Z"))
        }
    }
}
