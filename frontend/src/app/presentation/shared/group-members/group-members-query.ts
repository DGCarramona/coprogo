import { queryOptions } from '@tanstack/angular-query-experimental';
import { firstValueFrom } from 'rxjs';

import { GroupMembersPort } from '../../../application/group/group-members.port';
import type { GroupMember } from '../../../domain/group/group-member';

export const groupMembersQueryKey = (groupId: string | null) =>
  ['groups', groupId, 'members'] as const;

export const groupMembersQueryOptions = (groupId: string | null, port: GroupMembersPort) =>
  queryOptions({
    queryKey: groupMembersQueryKey(groupId),
    queryFn: (): Promise<readonly GroupMember[]> => {
      if (groupId === null) {
        throw new Error('Un groupe doit etre initialise avant de charger ses membres.');
      }

      return firstValueFrom(port.listByGroup(groupId));
    },
    select: (members): readonly string[] => members.map((member) => member.member),
    enabled: groupId !== null,
    staleTime: 30_000,
  });
