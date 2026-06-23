package tech.justdev.infrastructure.persistence.group

import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.impl.DSL
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.infrastructure.persistence.jooq.Tables.MEMBERS
import tech.justdev.infrastructure.persistence.jooq.dsl
import java.time.ZoneOffset

@Singleton
class R2dbcMemberRepository(
    @Named("default")
    private val connectionFactory: ConnectionFactory,
) : MemberRepository {
    override suspend fun findByEmail(email: MemberEmail): Member? =
        connectionFactory
            .dsl()
            .select(MEMBERS.EMAIL, MEMBERS.CREATED_AT)
            .from(MEMBERS)
            .where(MEMBERS.EMAIL.eq(email.toPrimitive()))
            .awaitFirstOrNull()
            ?.let { row ->
                Member(
                    email = MemberEmail.of(row.get(MEMBERS.EMAIL)),
                    createdAt = row.get(MEMBERS.CREATED_AT).toInstant(),
                )
            }

    override suspend fun persist(member: Member) {
        connectionFactory
            .dsl()
            .insertInto(MEMBERS)
            .columns(MEMBERS.EMAIL, MEMBERS.CREATED_AT)
            .values(member.email.toPrimitive(), member.createdAt.atOffset(ZoneOffset.UTC))
            .onConflict(MEMBERS.EMAIL)
            .doUpdate()
            .set(MEMBERS.EMAIL, DSL.excluded(MEMBERS.EMAIL))
            .awaitFirstOrNull()
    }
}
