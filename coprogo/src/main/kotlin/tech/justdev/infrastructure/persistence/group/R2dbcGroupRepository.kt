package tech.justdev.infrastructure.persistence.group

import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.Record
import org.jooq.ResultQuery
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.GroupMember
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.infrastructure.persistence.jooq.Tables.GROUPS
import tech.justdev.infrastructure.persistence.jooq.Tables.GROUP_MEMBERSHIPS
import tech.justdev.infrastructure.persistence.jooq.dsl
import tech.justdev.infrastructure.persistence.jooq.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Singleton
open class R2dbcGroupRepository(
    @Named("default")
    private val connectionFactory: ConnectionFactory,
) : GroupRepository {
    override suspend fun findById(id: GroupId): Group? {
        val dsl = connectionFactory.dsl()
        val group =
            dsl
                .select(GROUPS.ID, GROUPS.CREATED_BY, GROUPS.CREATED_AT)
                .from(GROUPS)
                .where(GROUPS.ID.eq(id.toPrimitive()))
                .awaitFirstOrNull()
                ?: return null

        val members =
            dsl
                .select(GROUP_MEMBERSHIPS.MEMBER_EMAIL, GROUP_MEMBERSHIPS.JOINED_AT)
                .from(GROUP_MEMBERSHIPS)
                .where(GROUP_MEMBERSHIPS.GROUP.eq(id.toPrimitive()))
                .awaitList()
                .map { membership -> membership.toDomain() }
                .toSet()

        return group.toDomain(members = members)
    }

    override suspend fun persist(group: Group) {
        connectionFactory.transaction {
            persistInTransaction(group)
        }
    }

    private suspend fun persistInTransaction(group: Group) {
        val dsl = connectionFactory.dsl()

        dsl
            .insertInto(GROUPS)
            .columns(GROUPS.ID, GROUPS.CREATED_BY, GROUPS.CREATED_AT)
            .values(group.id.toPrimitive(), group.createdBy.toPrimitive(), group.createdAt.atOffset(ZoneOffset.UTC))
            .onConflict(GROUPS.ID)
            .doUpdate()
            .set(GROUPS.CREATED_BY, GROUPS.CREATED_BY)
            .set(GROUPS.CREATED_AT, GROUPS.CREATED_AT)
            .awaitFirstOrNull()

        dsl.deleteFrom(GROUP_MEMBERSHIPS).where(GROUP_MEMBERSHIPS.GROUP.eq(group.id.toPrimitive())).awaitFirstOrNull()
        group.members.forEach { member ->
            dsl
                .insertInto(GROUP_MEMBERSHIPS)
                .columns(GROUP_MEMBERSHIPS.ID, GROUP_MEMBERSHIPS.GROUP, GROUP_MEMBERSHIPS.MEMBER_EMAIL, GROUP_MEMBERSHIPS.JOINED_AT)
                .values(UUID.randomUUID(), group.id.toPrimitive(), member.member.toPrimitive(), member.joinedAt.atOffset(ZoneOffset.UTC))
                .awaitFirstOrNull()
        }
    }
}

private fun org.jooq.Record3<UUID, String, OffsetDateTime>.toDomain(members: Set<GroupMember>): Group =
    Group(
        id = GroupId(value1()),
        createdBy = MemberEmail.of(value2()),
        createdAt = value3().toInstant(),
        members = members,
    )

private fun org.jooq.Record2<String, OffsetDateTime>.toDomain(): GroupMember =
    GroupMember(
        member = MemberEmail.of(value1()),
        joinedAt = value2().toInstant(),
    )

private suspend fun <R : Record> ResultQuery<R>.awaitList(): List<R> = asFlow().toList()
