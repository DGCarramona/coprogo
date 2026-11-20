package tech.justdev.application.group

import tech.justdev.domain.group.valueobject.MemberEmail
import tech.justdev.domain.shared.valueobject.GroupId

sealed class GroupApplicationException(
    message: String,
) : RuntimeException(message)

class GroupNotFoundException(
    group: GroupId,
) : GroupApplicationException("group ${group.toPrimitive()} was not found")

class GroupAccessDeniedException(
    group: GroupId,
    member: MemberEmail,
) : GroupApplicationException("member ${member.toPrimitive()} is not part of group ${group.toPrimitive()}")

class GroupCreatorRequiredException(
    group: GroupId,
    member: MemberEmail,
) : GroupApplicationException("member ${member.toPrimitive()} is not the creator of group ${group.toPrimitive()}")

class OwnershipShareChangeForbiddenException(
    group: GroupId,
    member: MemberEmail,
) : GroupApplicationException(
        "member ${member.toPrimitive()} is not allowed to modify ownership shares for group ${group.toPrimitive()}",
    )
