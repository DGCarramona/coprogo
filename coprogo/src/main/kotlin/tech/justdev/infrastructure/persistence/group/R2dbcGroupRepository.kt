package tech.justdev.infrastructure.persistence.group

import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.impl.DSL
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.GroupMember
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.valueobject.GroupName
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.infrastructure.persistence.jooq.Tables.GROUPS
import tech.justdev.infrastructure.persistence.jooq.Tables.GROUP_MEMBERSHIPS
import tech.justdev.infrastructure.persistence.jooq.Tables.MEMBERS
import tech.justdev.infrastructure.persistence.jooq.awaitList
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
                .select(GROUPS.ID, GROUPS.CREATED_BY, GROUPS.CREATED_AT, GROUPS.NAME)
                .from(GROUPS)
                .where(GROUPS.ID.eq(id.toPrimitive()))
                .awaitFirstOrNull()
                ?: return null

        val members =
            dsl
                .select(GROUP_MEMBERSHIPS.MEMBER_EMAIL, GROUP_MEMBERSHIPS.JOINED_AT, GROUP_MEMBERSHIPS.ADDED_BY)
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
            .insertInto(MEMBERS)
            .columns(MEMBERS.EMAIL, MEMBERS.CREATED_AT)
            .valuesOfRows(
                group.members
                    .sortedBy { member -> member.member.toPrimitive() }
                    .map { member ->
                        DSL.row(member.member.toPrimitive(), member.joinedAt.atOffset(ZoneOffset.UTC))
                    },
            ).onConflict(MEMBERS.EMAIL)
            .doNothing()
            .awaitFirstOrNull()

        dsl
            .insertInto(GROUPS)
            .columns(GROUPS.ID, GROUPS.CREATED_BY, GROUPS.CREATED_AT, GROUPS.NAME)
            .values(
                group.id.toPrimitive(),
                group.createdBy.toPrimitive(),
                group.createdAt.atOffset(ZoneOffset.UTC),
                group.name?.toPrimitive(),
            ).onConflict(GROUPS.ID)
            .doUpdate()
            .set(GROUPS.CREATED_BY, GROUPS.CREATED_BY)
            .set(GROUPS.CREATED_AT, GROUPS.CREATED_AT)
            .set(GROUPS.NAME, DSL.excluded(GROUPS.NAME))
            .awaitFirstOrNull()

        dsl
            .insertInto(GROUP_MEMBERSHIPS)
            .columns(
                GROUP_MEMBERSHIPS.ID,
                GROUP_MEMBERSHIPS.GROUP,
                GROUP_MEMBERSHIPS.MEMBER_EMAIL,
                GROUP_MEMBERSHIPS.JOINED_AT,
                GROUP_MEMBERSHIPS.ADDED_BY,
            ).valuesOfRows(
                group.members.map { member ->
                    DSL.row(
                        UUID.randomUUID(),
                        group.id.toPrimitive(),
                        member.member.toPrimitive(),
                        member.joinedAt.atOffset(ZoneOffset.UTC),
                        member.addedBy?.toPrimitive(),
                    )
                },
            ).onConflict(GROUP_MEMBERSHIPS.GROUP, GROUP_MEMBERSHIPS.MEMBER_EMAIL)
            .doNothing()
            .awaitFirstOrNull()
    }
}

private fun org.jooq.Record4<UUID, String, OffsetDateTime, String?>.toDomain(members: Set<GroupMember>): Group =
    Group(
        id = GroupId(value1()),
        createdBy = MemberEmail.of(value2()),
        createdAt = value3().toInstant(),
        members = members,
        name = value4()?.let(GroupName::of),
    )

private fun org.jooq.Record3<String, OffsetDateTime, String?>.toDomain(): GroupMember =
    GroupMember(
        member = MemberEmail.of(value1()),
        joinedAt = value2().toInstant(),
        addedBy = value3()?.let(MemberEmail::of),
    )
