package tech.justdev.domain.group.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.testsupport.groupId
import tech.justdev.testsupport.groupInvitationId
import tech.justdev.testsupport.memberEmail
import java.time.Instant

class GroupInvitationTest {
    @Test
    fun `invitation should require both acceptance fields together`() {
        assertThrows(IllegalArgumentException::class.java) {
            invitation(acceptedBy = memberEmail("invited"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            invitation(acceptedAt = ACCEPTED_AT)
        }
    }

    @Test
    fun `accept should record acceptance by the invited member`() {
        val invitation = invitation()

        val accepted = invitation.accept(INVITED, ACCEPTED_AT)

        assertEquals(INVITED, accepted.acceptedBy)
        assertEquals(ACCEPTED_AT, accepted.acceptedAt)
        assertFalse(accepted.isPending())
    }

    @Test
    fun `new invitation should be pending`() {
        assertTrue(invitation().isPending())
    }

    @Test
    fun `accept should reject another member`() {
        assertThrows(IllegalArgumentException::class.java) {
            invitation().accept(memberEmail("other-member"), ACCEPTED_AT)
        }
    }

    @Test
    fun `accept should reject an already accepted invitation`() {
        val accepted = invitation().accept(INVITED, ACCEPTED_AT)

        assertThrows(IllegalArgumentException::class.java) {
            accepted.accept(INVITED, ACCEPTED_AT.plusSeconds(1))
        }
    }

    private fun invitation(
        acceptedBy: MemberEmail? = null,
        acceptedAt: Instant? = null,
    ): GroupInvitation =
        GroupInvitation(
            id = groupInvitationId("domain-invitation"),
            group = groupId("domain-invitation-group"),
            invitedMember = INVITED,
            invitedBy = memberEmail("inviter"),
            invitedAt = Instant.parse("2026-04-14T09:00:00Z"),
            acceptedBy = acceptedBy,
            acceptedAt = acceptedAt,
        )

    private companion object {
        val INVITED = memberEmail("invited")
        val ACCEPTED_AT: Instant = Instant.parse("2026-04-14T10:00:00Z")
    }
}
