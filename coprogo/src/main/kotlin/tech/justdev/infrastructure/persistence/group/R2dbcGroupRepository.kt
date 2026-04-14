package tech.justdev.infrastructure.persistence.group

import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import kotlinx.coroutines.flow.collect
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.GroupMember
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId
import java.util.UUID

@Singleton
open class R2dbcGroupRepository(
    private val groupDataRepository: GroupDataRepository,
    private val groupMembershipDataRepository: GroupMembershipDataRepository,
) : GroupRepository {
    override suspend fun findById(id: GroupId): Group? {
        val group = groupDataRepository.findById(id.toPrimitive()) ?: return null
        val members =
            groupMembershipDataRepository
                .findByGroup(id.toPrimitive())
                .map { membership -> membership.toDomain() }
                .toSet()

        return group.toDomain(members = members)
    }

    @Transactional
    override suspend fun persist(group: Group) {
        groupDataRepository.upsert(
            id = group.id.toPrimitive(),
            createdBy = group.createdBy.toPrimitive(),
            createdAt = group.createdAt,
        )

        groupMembershipDataRepository.deleteByGroup(group.id.toPrimitive())
        groupMembershipDataRepository
            .saveAll(group.members.map { member -> member.toEntity(group.id) })
            .collect()
    }
}

private fun GroupEntity.toDomain(members: Set<GroupMember>): Group =
    Group(
        id = GroupId(id),
        createdBy = MemberEmail.of(createdBy),
        createdAt = createdAt,
        members = members,
    )

private fun GroupMembershipEntity.toDomain(): GroupMember =
    GroupMember(
        member = MemberEmail.of(memberEmail),
        joinedAt = joinedAt,
    )

private fun GroupMember.toEntity(group: GroupId): GroupMembershipEntity =
    GroupMembershipEntity(
        id = UUID.randomUUID(),
        group = group.toPrimitive(),
        memberEmail = member.toPrimitive(),
        joinedAt = joinedAt,
    )
