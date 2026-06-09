package tech.justdev.infrastructure.persistence.revenue

import io.micronaut.data.annotation.Query
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

@R2dbcRepository(dialect = Dialect.POSTGRES)
interface OwnershipShareChangeDataRepository : CoroutineCrudRepository<OwnershipShareChangeEntity, UUID> {
    @Query(
        value =
            """
            SELECT *
            FROM ownership_share_changes
            WHERE "group" = :group
            ORDER BY effective_date, recorded_at
            """,
        nativeQuery = true,
    )
    suspend fun findByGroup(group: UUID): List<OwnershipShareChangeEntity>
}

@R2dbcRepository(dialect = Dialect.POSTGRES)
interface OwnershipShareAllocationDataRepository : CoroutineCrudRepository<OwnershipShareAllocationEntity, UUID> {
    @Query(
        value =
            """
            SELECT allocation.*
            FROM ownership_share_allocations allocation
            JOIN ownership_share_changes change ON change.id = allocation.change_id
            WHERE change."group" = :group
            ORDER BY change.effective_date, allocation.member_email
            """,
        nativeQuery = true,
    )
    suspend fun findByGroup(group: UUID): List<OwnershipShareAllocationEntity>
}
