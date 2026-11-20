package tech.justdev.application.group

import jakarta.inject.Singleton
import tech.justdev.application.shared.TransactionRunner
import tech.justdev.domain.group.repository.GroupRepository
import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId
import java.time.Instant

data class AddMemberToGroupCommand(
    val group: GroupId,
    val addedBy: MemberEmail,
    val member: MemberEmail,
    val addedAt: Instant,
)

interface AddMemberToGroupUseCase {
    suspend operator fun invoke(command: AddMemberToGroupCommand)
}

@Singleton
class AddMemberToGroupUseCaseImpl(
    private val groupAccessPolicy: GroupAccessPolicy,
    private val groupRepository: GroupRepository,
    private val transactionRunner: TransactionRunner,
) : AddMemberToGroupUseCase {
    override suspend operator fun invoke(command: AddMemberToGroupCommand) {
        transactionRunner.transaction {
            val group = groupAccessPolicy.requireMember(command.group, command.addedBy)
            require(!group.contains(command.member)) {
                "member ${command.member.toPrimitive()} is already part of group ${group.id.toPrimitive()}"
            }

            groupRepository.persist(group.addMember(command.member, command.addedAt, command.addedBy))
        }
    }
}
