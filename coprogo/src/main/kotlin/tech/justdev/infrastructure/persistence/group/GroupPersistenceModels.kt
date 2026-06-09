package tech.justdev.infrastructure.persistence.group

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import java.time.Instant
import java.util.UUID

@MappedEntity("members")
data class MemberEntity(
    @field:Id
    val email: String,
    @field:MappedProperty("created_at")
    val createdAt: Instant,
)

@MappedEntity("groups")
data class GroupEntity(
    @field:Id
    val id: UUID,
    @field:MappedProperty("created_by")
    val createdBy: String,
    @field:MappedProperty("created_at")
    val createdAt: Instant,
)

@MappedEntity("group_memberships")
data class GroupMembershipEntity(
    @field:Id
    val id: UUID,
    @field:MappedProperty("group")
    val group: UUID,
    @field:MappedProperty("member_email")
    val memberEmail: String,
    @field:MappedProperty("joined_at")
    val joinedAt: Instant,
)

@MappedEntity("group_invitations")
data class GroupInvitationEntity(
    @field:Id
    val id: UUID,
    @field:MappedProperty("group")
    val group: UUID,
    @field:MappedProperty("invited_email")
    val invitedEmail: String,
    @field:MappedProperty("invited_by")
    val invitedBy: String,
    @field:MappedProperty("invited_at")
    val invitedAt: Instant,
    @field:MappedProperty("accepted_by")
    val acceptedBy: String?,
    @field:MappedProperty("accepted_at")
    val acceptedAt: Instant?,
)
