package tech.justdev.application.group

import jakarta.inject.Singleton
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.valueobject.GroupName
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId

data class RenameGroupCommand(
    val group: GroupId,
    val renamedBy: MemberEmail,
    val name: GroupName,
)

interface RenameGroupUseCase {
    suspend operator fun invoke(command: RenameGroupCommand)
}

@Singleton
class RenameGroupUseCaseImpl(
    private val groupAccessPolicy: GroupAccessPolicy,
    private val groupRepository: GroupRepository,
) : RenameGroupUseCase {
    override suspend fun invoke(command: RenameGroupCommand) {
        val group = groupAccessPolicy.requireMember(command.group, command.renamedBy)
        if (group.createdBy != command.renamedBy) {
            throw GroupCreatorRequiredException(command.group, command.renamedBy)
        }
        groupRepository.persist(group.rename(command.name))
    }
}
