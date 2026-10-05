import { QueryClient } from '@tanstack/angular-query-experimental';
import { Observable, of } from 'rxjs';

import { GroupMembersPort } from '../../../application/group/group-members.port';
import type { GroupMember } from '../../../domain/group/group-member';
import { groupMembersQueryOptions } from './group-members-query';

describe('groupMembersQueryOptions', () => {
  it('keeps an uninitialized group query disabled and scoped', () => {
    const options = groupMembersQueryOptions(null, new StubGroupMembersPort());

    expect({
      queryKey: options.queryKey,
      enabled: options.enabled,
      staleTime: options.staleTime,
    }).toEqual({
      queryKey: ['groups', null, 'members'],
      enabled: false,
      staleTime: 30_000,
    });
  });

  it('loads group members through the port and projects their addresses', async () => {
    const port = new StubGroupMembersPort();
    const options = groupMembersQueryOptions('group-1', port);

    const members = await new QueryClient().fetchQuery(options);

    expect({
      requestedGroupIds: port.requestedGroupIds,
      members,
      addresses: options.select?.(members),
    }).toEqual({
      requestedGroupIds: ['group-1'],
      members: port.members,
      addresses: ['alice@example.com', 'bob@example.com'],
    });
  });
});

class StubGroupMembersPort extends GroupMembersPort {
  readonly members: readonly GroupMember[] = [
    { member: 'alice@example.com', joinedAt: new Date('2026-01-01T00:00:00Z') },
    { member: 'bob@example.com', joinedAt: new Date('2026-01-02T00:00:00Z') },
  ];
  readonly requestedGroupIds: string[] = [];

  override listByGroup(groupId: string): Observable<readonly GroupMember[]> {
    this.requestedGroupIds.push(groupId);
    return of(this.members);
  }
}
