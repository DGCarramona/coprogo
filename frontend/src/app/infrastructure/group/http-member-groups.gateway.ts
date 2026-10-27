import { Injectable } from '@angular/core';
import { catchError, firstValueFrom, map, throwError } from 'rxjs';

import { MemberGroupsPort } from '../../application/group/member-groups.port';
import type { MemberGroup } from '../../domain/group/member-group';
import { GroupsService } from '../api/generated';
import { toApiClientError } from '../api/api-client.error';

@Injectable({ providedIn: 'root' })
export class HttpMemberGroupsGateway extends MemberGroupsPort {
  constructor(private readonly groupsService: GroupsService) {
    super();
  }

  override list(): Promise<readonly MemberGroup[]> {
    return firstValueFrom(
      this.groupsService.listMemberGroups().pipe(
        map((groups) =>
          groups.map((group): MemberGroup => ({
            groupId: group.group,
            createdBy: group.createdBy,
            createdAt: new Date(group.createdAt),
            name: group.name ?? null,
            canRename: group.canRename ?? false,
          })),
        ),
        catchError((error: unknown) =>
          throwError(() => toApiClientError(error, 'Vos groupes n’ont pas pu être chargés.')),
        ),
      ),
    );
  }
}
