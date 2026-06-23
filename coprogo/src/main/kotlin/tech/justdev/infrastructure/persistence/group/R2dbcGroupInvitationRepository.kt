package tech.justdev.infrastructure.persistence.group

import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.Record7
import org.jooq.ResultQuery
import org.jooq.SelectJoinStep
import org.jooq.impl.DSL
import tech.justdev.domain.group.entity.GroupInvitation
import tech.justdev.domain.group.entity.GroupInvitationId
import tech.justdev.domain.group.repository.GroupInvitationRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.infrastructure.persistence.jooq.Tables.GROUP_INVITATIONS
import tech.justdev.infrastructure.persistence.jooq.dsl
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

private typealias InvitationRecord = Record7<UUID, UUID, String, String, OffsetDateTime, String?, OffsetDateTime?>

@Singleton
class R2dbcGroupInvitationRepository(
    @Named("default")
    private val connectionFactory: ConnectionFactory,
) : GroupInvitationRepository {
    override suspend fun findById(id: GroupInvitationId): GroupInvitation? =
        connectionFactory
            .dsl()
            .selectInvitation()
            .where(GROUP_INVITATIONS.ID.eq(id.toPrimitive()))
            .awaitFirstOrNull()
            ?.toDomain()

    override suspend fun findPendingByGroup(group: GroupId): List<GroupInvitation> =
        connectionFactory
            .dsl()
            .selectInvitation()
            .where(GROUP_INVITATIONS.GROUP.eq(group.toPrimitive()).and(GROUP_INVITATIONS.ACCEPTED_AT.isNull))
            .orderBy(GROUP_INVITATIONS.INVITED_EMAIL)
            .awaitList()
            .map { invitation -> invitation.toDomain() }

    override suspend fun findPendingByInvitedMember(invitedMember: MemberEmail): List<GroupInvitation> =
        connectionFactory
            .dsl()
            .selectInvitation()
            .where(GROUP_INVITATIONS.INVITED_EMAIL.eq(invitedMember.toPrimitive()).and(GROUP_INVITATIONS.ACCEPTED_AT.isNull))
            .orderBy(GROUP_INVITATIONS.INVITED_AT)
            .awaitList()
            .map { invitation -> invitation.toDomain() }

    override suspend fun persist(invitation: GroupInvitation) {
        connectionFactory
            .dsl()
            .insertInto(GROUP_INVITATIONS)
            .columns(
                GROUP_INVITATIONS.ID,
                GROUP_INVITATIONS.GROUP,
                GROUP_INVITATIONS.INVITED_EMAIL,
                GROUP_INVITATIONS.INVITED_BY,
                GROUP_INVITATIONS.INVITED_AT,
                GROUP_INVITATIONS.ACCEPTED_BY,
                GROUP_INVITATIONS.ACCEPTED_AT,
            ).values(
                invitation.id.toPrimitive(),
                invitation.group.toPrimitive(),
                invitation.invitedMember.toPrimitive(),
                invitation.invitedBy.toPrimitive(),
                invitation.invitedAt.atOffset(ZoneOffset.UTC),
                invitation.acceptedBy?.toPrimitive(),
                invitation.acceptedAt?.atOffset(ZoneOffset.UTC),
            ).onConflict(GROUP_INVITATIONS.GROUP, GROUP_INVITATIONS.INVITED_EMAIL)
            .doUpdate()
            .set(GROUP_INVITATIONS.ID, DSL.excluded(GROUP_INVITATIONS.ID))
            .set(GROUP_INVITATIONS.ACCEPTED_BY, DSL.excluded(GROUP_INVITATIONS.ACCEPTED_BY))
            .set(GROUP_INVITATIONS.ACCEPTED_AT, DSL.excluded(GROUP_INVITATIONS.ACCEPTED_AT))
            .awaitFirstOrNull()
    }
}

private fun org.jooq.DSLContext.selectInvitation(): SelectJoinStep<InvitationRecord> =
    select(
        GROUP_INVITATIONS.ID,
        GROUP_INVITATIONS.GROUP,
        GROUP_INVITATIONS.INVITED_EMAIL,
        GROUP_INVITATIONS.INVITED_BY,
        GROUP_INVITATIONS.INVITED_AT,
        GROUP_INVITATIONS.ACCEPTED_BY,
        GROUP_INVITATIONS.ACCEPTED_AT,
    ).from(GROUP_INVITATIONS)

private fun InvitationRecord.toDomain(): GroupInvitation =
    GroupInvitation(
        id = GroupInvitationId(value1()),
        group = GroupId(value2()),
        invitedMember = MemberEmail.of(value3()),
        invitedBy = MemberEmail.of(value4()),
        invitedAt = value5().toInstant(),
        acceptedBy = value6()?.let(MemberEmail::of),
        acceptedAt = value7()?.toInstant(),
    )

private suspend fun <R : org.jooq.Record> ResultQuery<R>.awaitList(): List<R> = asFlow().toList()
