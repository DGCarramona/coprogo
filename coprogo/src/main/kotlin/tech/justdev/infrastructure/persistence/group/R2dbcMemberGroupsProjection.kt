package tech.justdev.infrastructure.persistence.group

import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Named
import jakarta.inject.Singleton
import tech.justdev.application.group.MemberGroupSnapshot
import tech.justdev.application.group.MemberGroupsProjection
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.infrastructure.persistence.jooq.Tables.GROUPS
import tech.justdev.infrastructure.persistence.jooq.Tables.GROUP_MEMBERSHIPS
import tech.justdev.infrastructure.persistence.jooq.awaitList
import tech.justdev.infrastructure.persistence.jooq.dsl

@Singleton
class R2dbcMemberGroupsProjection(
    @param:Named("default") private val connectionFactory: ConnectionFactory,
) : MemberGroupsProjection {
    override suspend fun findByMember(member: MemberEmail): List<MemberGroupSnapshot> =
        connectionFactory
            .dsl()
            .select(GROUPS.ID, GROUPS.CREATED_BY, GROUPS.CREATED_AT)
            .from(GROUPS)
            .join(GROUP_MEMBERSHIPS)
            .on(GROUP_MEMBERSHIPS.GROUP.eq(GROUPS.ID))
            .where(GROUP_MEMBERSHIPS.MEMBER_EMAIL.eq(member.toPrimitive()))
            .orderBy(GROUPS.CREATED_AT.asc(), GROUPS.ID.asc())
            .awaitList()
            .map { row -> MemberGroupSnapshot(row.value1(), row.value2(), row.value3().toInstant()) }
}
