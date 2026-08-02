import { Injectable } from '@angular/core';
import { catchError, map, Observable, throwError } from 'rxjs';

import { GroupMembersPort } from '../../application/group/group-members.port';
import { GroupMember } from '../../domain/group/group-member';
import { GroupMemberResponseDto, GroupsService } from '../api/generated';
import { toApiClientError } from '../api/api-client.error';

@Injectable({ providedIn: 'root' })
export class HttpGroupMembersGateway extends GroupMembersPort {
  constructor(private readonly groupsService: GroupsService) {
    super();
  }

  override listByGroup(groupId: string): Observable<readonly GroupMember[]> {
    return this.groupsService.get(groupId).pipe(
      map((group) => group.members.map(mapGroupMemberResponseDtoToDomain)),
      catchError((error: unknown) =>
        throwError(() =>
          toApiClientError(error, 'Les membres du groupe n ont pas pu etre charges.'),
        ),
      ),
    );
  }
}

const mapGroupMemberResponseDtoToDomain = (member: GroupMemberResponseDto): GroupMember => ({
  member: member.member,
  joinedAt: new Date(member.joinedAt),
});
