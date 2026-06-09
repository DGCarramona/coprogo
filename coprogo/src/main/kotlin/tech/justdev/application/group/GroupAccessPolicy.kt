package tech.justdev.application.group

import jakarta.inject.Singleton
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId

@Singleton
class GroupAccessPolicy(
    private val groupRepository: GroupRepository,
) {
    suspend fun requireMember(
        group: GroupId,
        actor: MemberEmail,
    ): Group {
        val existingGroup = groupRepository.findById(group) ?: throw GroupNotFoundException(group)
        if (!existingGroup.contains(actor)) {
            throw GroupAccessDeniedException(group, actor)
        }

        return existingGroup
    }

    suspend fun requireCreator(
        group: GroupId,
        actor: MemberEmail,
    ): Group {
        val existingGroup = groupRepository.findById(group) ?: throw GroupNotFoundException(group)
        if (existingGroup.createdBy != actor) {
            throw GroupCreatorRequiredException(group, actor)
        }

        return existingGroup
    }
}
