package tech.justdev.infrastructure.persistence.revenue

import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.Record5
import org.jooq.Record6
import org.jooq.ResultQuery
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.revenue.entity.OwnershipShareChange
import tech.justdev.domain.revenue.entity.OwnershipShareChangeId
import tech.justdev.domain.revenue.entity.OwnershipShareTimeline
import tech.justdev.domain.revenue.repository.OwnershipShareTimelineRepository
import tech.justdev.domain.revenue.valueobject.OwnershipPercentage
import tech.justdev.domain.revenue.valueobject.OwnershipShare
import tech.justdev.domain.shared.valueobject.GroupId
import tech.justdev.infrastructure.persistence.jooq.Tables.OWNERSHIP_SHARE_ALLOCATIONS
import tech.justdev.infrastructure.persistence.jooq.Tables.OWNERSHIP_SHARE_CHANGES
import tech.justdev.infrastructure.persistence.jooq.dsl
import tech.justdev.infrastructure.persistence.jooq.transaction
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Singleton
open class R2dbcOwnershipShareTimelineRepository(
    @Named("default")
    private val connectionFactory: ConnectionFactory,
) : OwnershipShareTimelineRepository {
    override suspend fun findByGroup(group: GroupId): OwnershipShareTimeline? {
        val dsl = connectionFactory.dsl()
        val changes = dsl.findChangesByGroup(group.toPrimitive())
        if (changes.isEmpty()) {
            return null
        }

        val allocationsByChange =
            dsl
                .findAllocationsByGroup(group.toPrimitive())
                .groupBy { allocation -> allocation.value2() }

        return OwnershipShareTimeline(
            group = group,
            changes = changes.map { change -> change.toDomain(allocationsByChange.getValue(change.value1())) },
        )
    }

    override suspend fun persist(timeline: OwnershipShareTimeline) {
        connectionFactory.transaction {
            persistInTransaction(timeline)
        }
    }

    private suspend fun persistInTransaction(timeline: OwnershipShareTimeline) {
        val dsl = connectionFactory.dsl()
        val group = timeline.group.toPrimitive()
        val existingChanges = dsl.findChangesByGroup(group).map { change -> change.value1() }.toSet()
        val existingAllocations =
            dsl
                .findAllocationsByGroup(group)
                .map { allocation -> allocation.value2() to allocation.value3() }
                .toSet()

        timeline
            .history()
            .filterNot { change -> change.id.toPrimitive() in existingChanges }
            .forEach { change -> dsl.persist(change, timeline.group) }

        timeline
            .history()
            .flatMap { change ->
                change.shares
                    .filterNot { share -> change.id.toPrimitive() to share.member.toPrimitive() in existingAllocations }
                    .map { share -> change.id to share }
            }.forEach { (change, share) -> dsl.persist(share, change) }
    }
}

private suspend fun org.jooq.DSLContext.findChangesByGroup(group: UUID): List<Record5<UUID, UUID, LocalDate, String, OffsetDateTime>> =
    select(
        OWNERSHIP_SHARE_CHANGES.ID,
        OWNERSHIP_SHARE_CHANGES.GROUP,
        OWNERSHIP_SHARE_CHANGES.EFFECTIVE_DATE,
        OWNERSHIP_SHARE_CHANGES.RECORDED_BY,
        OWNERSHIP_SHARE_CHANGES.RECORDED_AT,
    ).from(OWNERSHIP_SHARE_CHANGES)
        .where(OWNERSHIP_SHARE_CHANGES.GROUP.eq(group))
        .orderBy(OWNERSHIP_SHARE_CHANGES.EFFECTIVE_DATE, OWNERSHIP_SHARE_CHANGES.RECORDED_AT)
        .awaitList()

private suspend fun org.jooq.DSLContext.findAllocationsByGroup(group: UUID): List<Record6<UUID, UUID, String, Int, LocalDate, String>> =
    select(
        OWNERSHIP_SHARE_ALLOCATIONS.ID,
        OWNERSHIP_SHARE_ALLOCATIONS.CHANGE_ID,
        OWNERSHIP_SHARE_ALLOCATIONS.MEMBER_EMAIL,
        OWNERSHIP_SHARE_ALLOCATIONS.BASIS_POINTS,
        OWNERSHIP_SHARE_CHANGES.EFFECTIVE_DATE,
        OWNERSHIP_SHARE_CHANGES.RECORDED_BY,
    ).from(OWNERSHIP_SHARE_ALLOCATIONS)
        .join(OWNERSHIP_SHARE_CHANGES)
        .on(OWNERSHIP_SHARE_CHANGES.ID.eq(OWNERSHIP_SHARE_ALLOCATIONS.CHANGE_ID))
        .where(OWNERSHIP_SHARE_CHANGES.GROUP.eq(group))
        .orderBy(OWNERSHIP_SHARE_CHANGES.EFFECTIVE_DATE, OWNERSHIP_SHARE_ALLOCATIONS.MEMBER_EMAIL)
        .awaitList()

private suspend fun org.jooq.DSLContext.persist(
    change: OwnershipShareChange,
    group: GroupId,
) {
    insertInto(OWNERSHIP_SHARE_CHANGES)
        .columns(
            OWNERSHIP_SHARE_CHANGES.ID,
            OWNERSHIP_SHARE_CHANGES.GROUP,
            OWNERSHIP_SHARE_CHANGES.EFFECTIVE_DATE,
            OWNERSHIP_SHARE_CHANGES.RECORDED_BY,
            OWNERSHIP_SHARE_CHANGES.RECORDED_AT,
        ).values(
            change.id.toPrimitive(),
            group.toPrimitive(),
            change.effectiveDate,
            change.recordedBy.toPrimitive(),
            change.recordedAt.atOffset(ZoneOffset.UTC),
        ).awaitFirstOrNull()
}

private suspend fun org.jooq.DSLContext.persist(
    share: OwnershipShare,
    change: OwnershipShareChangeId,
) {
    insertInto(OWNERSHIP_SHARE_ALLOCATIONS)
        .columns(
            OWNERSHIP_SHARE_ALLOCATIONS.ID,
            OWNERSHIP_SHARE_ALLOCATIONS.CHANGE_ID,
            OWNERSHIP_SHARE_ALLOCATIONS.MEMBER_EMAIL,
            OWNERSHIP_SHARE_ALLOCATIONS.BASIS_POINTS,
        ).values(UUID.randomUUID(), change.toPrimitive(), share.member.toPrimitive(), share.percentage.inBasisPoints())
        .awaitFirstOrNull()
}

private fun Record5<UUID, UUID, LocalDate, String, OffsetDateTime>.toDomain(
    allocations: List<Record6<UUID, UUID, String, Int, LocalDate, String>>,
): OwnershipShareChange =
    OwnershipShareChange(
        id = OwnershipShareChangeId(value1()),
        effectiveDate = value3(),
        recordedBy = MemberEmail.of(value4()),
        recordedAt = value5().toInstant(),
        shares = allocations.map { allocation -> allocation.toDomain() }.toSet(),
    )

private fun Record6<UUID, UUID, String, Int, LocalDate, String>.toDomain(): OwnershipShare =
    OwnershipShare(
        member = MemberEmail.of(value3()),
        percentage = OwnershipPercentage.ofBasisPoints(value4()),
    )

private suspend fun <R : org.jooq.Record> ResultQuery<R>.awaitList(): List<R> = asFlow().toList()
