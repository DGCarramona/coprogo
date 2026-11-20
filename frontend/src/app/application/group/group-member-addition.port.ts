import { MemberEmail } from '../../domain/group/member-email';

export abstract class GroupMemberAdditionPort {
  abstract addMember(groupId: string, email: MemberEmail): Promise<void>;
}
