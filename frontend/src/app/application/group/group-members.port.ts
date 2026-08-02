import { Observable } from 'rxjs';

import { GroupMember } from '../../domain/group/group-member';

export abstract class GroupMembersPort {
  abstract listByGroup(groupId: string): Observable<readonly GroupMember[]>;
}
