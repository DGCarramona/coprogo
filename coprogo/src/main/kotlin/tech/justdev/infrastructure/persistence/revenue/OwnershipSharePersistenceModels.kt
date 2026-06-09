package tech.justdev.infrastructure.persistence.revenue

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@MappedEntity("ownership_share_changes")
data class OwnershipShareChangeEntity(
    @field:Id
    val id: UUID,
    @field:MappedProperty("group")
    val group: UUID,
    @field:MappedProperty("effective_date")
    val effectiveDate: LocalDate,
    @field:MappedProperty("recorded_by")
    val recordedBy: String,
    @field:MappedProperty("recorded_at")
    val recordedAt: Instant,
)

@MappedEntity("ownership_share_allocations")
data class OwnershipShareAllocationEntity(
    @field:Id
    val id: UUID,
    @field:MappedProperty("change_id")
    val changeId: UUID,
    @field:MappedProperty("member_email")
    val memberEmail: String,
    @field:MappedProperty("basis_points")
    val basisPoints: Int,
)
