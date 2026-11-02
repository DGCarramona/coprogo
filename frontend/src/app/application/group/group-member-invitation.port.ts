import type { MemberEmail } from '../../domain/group/member-email';

export abstract class GroupMemberInvitationPort {
  abstract invite(groupId: string, email: MemberEmail): Promise<void>;
}
