package tech.justdev.infrastructure.persistence.group

import io.micronaut.data.annotation.Query
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.time.Instant
import java.util.UUID

@R2dbcRepository(dialect = Dialect.POSTGRES)
interface MemberDataRepository : CoroutineCrudRepository<MemberEntity, String> {
    suspend fun findByEmail(email: String): MemberEntity?

    @Query(
        value =
            """
            INSERT INTO members (email, created_at)
            VALUES (:email, :createdAt)
            ON CONFLICT (email) DO UPDATE
            SET email = EXCLUDED.email
            """,
        nativeQuery = true,
        readOnly = false,
    )
    suspend fun upsertByEmail(
        email: String,
        createdAt: Instant,
    )
}

@R2dbcRepository(dialect = Dialect.POSTGRES)
interface GroupDataRepository : CoroutineCrudRepository<GroupEntity, UUID> {
    @Query(
        value =
            """
            INSERT INTO groups (id, created_by, created_at)
            VALUES (:id, :createdBy, :createdAt)
            ON CONFLICT (id) DO UPDATE
            SET created_by = groups.created_by,
                created_at = groups.created_at
            """,
        nativeQuery = true,
        readOnly = false,
    )
    suspend fun upsert(
        id: UUID,
        createdBy: String,
        createdAt: Instant,
    )
}

@R2dbcRepository(dialect = Dialect.POSTGRES)
interface GroupMembershipDataRepository : CoroutineCrudRepository<GroupMembershipEntity, UUID> {
    suspend fun findByGroup(group: UUID): List<GroupMembershipEntity>

    suspend fun deleteByGroup(group: UUID)
}

@R2dbcRepository(dialect = Dialect.POSTGRES)
interface GroupInvitationDataRepository : CoroutineCrudRepository<GroupInvitationEntity, UUID> {
    @Query(
        value =
            """
            SELECT *
            FROM group_invitations
            WHERE "group" = :group
              AND accepted_at IS NULL
            ORDER BY invited_email
            """,
        nativeQuery = true,
    )
    suspend fun findPendingByGroup(group: UUID): List<GroupInvitationEntity>

    @Query(
        value =
            """
            SELECT *
            FROM group_invitations
            WHERE invited_email = :invitedEmail
              AND accepted_at IS NULL
            ORDER BY invited_at
            """,
        nativeQuery = true,
    )
    suspend fun findPendingByInvitedEmail(invitedEmail: String): List<GroupInvitationEntity>

    @Query(
        value =
            """
            INSERT INTO group_invitations (
                id,
                "group",
                invited_email,
                invited_by,
                invited_at,
                accepted_by,
                accepted_at
            )
            VALUES (
                :id,
                :group,
                :invitedEmail,
                :invitedBy,
                :invitedAt,
                :acceptedBy,
                :acceptedAt
            )
            ON CONFLICT ("group", invited_email) DO UPDATE
            SET id = EXCLUDED.id,
                accepted_by = EXCLUDED.accepted_by,
                accepted_at = EXCLUDED.accepted_at
            """,
        nativeQuery = true,
        readOnly = false,
    )
    suspend fun upsert(
        id: UUID,
        group: UUID,
        invitedEmail: String,
        invitedBy: String,
        invitedAt: Instant,
        acceptedBy: String?,
        acceptedAt: Instant?,
    )
}
