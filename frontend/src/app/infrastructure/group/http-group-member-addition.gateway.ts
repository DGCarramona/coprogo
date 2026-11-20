import { Injectable } from '@angular/core';
import { catchError, firstValueFrom, map } from 'rxjs';

import { GroupMemberAdditionPort } from '../../application/group/group-member-addition.port';
import { MemberEmail } from '../../domain/group/member-email';
import { GroupsService } from '../api/generated';
import { toApiClientError } from '../api/api-client.error';

@Injectable({ providedIn: 'root' })
export class HttpGroupMemberAdditionGateway extends GroupMemberAdditionPort {
  constructor(private readonly groupsService: GroupsService) {
    super();
  }

  override addMember(groupId: string, email: MemberEmail): Promise<void> {
    return firstValueFrom(
      this.groupsService.addMember(groupId, { member: email }).pipe(
        catchError((error: unknown) => {
          throw toApiClientError(error, 'errors.members.add');
        }),
        map(() => undefined),
      ),
    );
  }
}
