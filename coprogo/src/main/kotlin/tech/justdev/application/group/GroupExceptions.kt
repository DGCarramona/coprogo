package tech.justdev.application.group

import tech.justdev.domain.group.entity.GroupInvitationId
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

class GroupInvitationNotFoundException(
    invitation: GroupInvitationId,
) : GroupApplicationException("group invitation ${invitation.toPrimitive()} was not found")

class GroupInvitationAlreadyExistsException(
    group: GroupId,
    invitedMember: MemberEmail,
) : GroupApplicationException(
        "group ${group.toPrimitive()} already has a pending invitation for ${invitedMember.toPrimitive()}",
    )

class GroupInvitationAlreadyAcceptedException(
    invitation: GroupInvitationId,
) : GroupApplicationException("group invitation ${invitation.toPrimitive()} is already accepted")

class GroupInvitationAccessDeniedException(
    invitation: GroupInvitationId,
    member: MemberEmail,
) : GroupApplicationException(
        "member ${member.toPrimitive()} cannot accept group invitation ${invitation.toPrimitive()}",
    )

class OwnershipShareChangeForbiddenException(
    group: GroupId,
    member: MemberEmail,
) : GroupApplicationException(
        "member ${member.toPrimitive()} is not allowed to modify ownership shares for group ${group.toPrimitive()}",
    )
