import { Injectable } from '@angular/core';
import { catchError, map, Observable, throwError } from 'rxjs';

import { GroupMembersPort } from '../../application/group/group-members.port';
import { GroupMember } from '../../domain/group/group-member';
import { mapArray } from '../../shared/rxjs/map-array';
import { GroupsService } from '../api/generated';
import { toApiClientError } from '../api/api-client.error';

@Injectable({ providedIn: 'root' })
export class HttpGroupMembersGateway extends GroupMembersPort {
  constructor(private readonly groupsService: GroupsService) {
    super();
  }

  override listByGroup(groupId: string): Observable<readonly GroupMember[]> {
    return this.groupsService.get(groupId).pipe(
      map((group) => group.members),
      mapArray(({ member, joinedAt }): GroupMember => ({
        member,
        joinedAt: new Date(joinedAt),
      })),
      catchError((error: unknown) =>
        throwError(() =>
          toApiClientError(error, 'Les membres du groupe n ont pas pu etre charges.'),
        ),
      ),
    );
  }
}
