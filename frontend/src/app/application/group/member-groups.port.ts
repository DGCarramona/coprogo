import type { MemberGroup } from '../../domain/group/member-group';

export abstract class MemberGroupsPort {
  abstract list(): Promise<readonly MemberGroup[]>;
}
