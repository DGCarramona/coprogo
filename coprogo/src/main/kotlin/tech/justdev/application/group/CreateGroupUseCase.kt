package tech.justdev.application.group

import jakarta.inject.Singleton
import tech.justdev.domain.group.entity.Group
import tech.justdev.domain.group.entity.Member
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.repository.MemberRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import java.time.Instant
import java.util.UUID

data class CreateGroupCommand(
    val createdBy: MemberEmail,
    val createdAt: Instant,
)

data class CreatedGroupResult(
    val group: UUID,
)

@Singleton
class CreateGroupUseCase(
    private val memberRepository: MemberRepository,
    private val groupRepository: GroupRepository,
    private val groupIdGenerator: GroupIdGenerator = RandomGroupIdGenerator,
) {
    suspend operator fun invoke(command: CreateGroupCommand): CreatedGroupResult {
        memberRepository.persist(
            Member(
                email = command.createdBy,
                createdAt = command.createdAt,
            ),
        )

        val group =
            Group.create(
                id = groupIdGenerator.next(),
                createdBy = command.createdBy,
                createdAt = command.createdAt,
            )

        groupRepository.persist(group)

        return CreatedGroupResult(group = group.id.toPrimitive())
    }
}
