import type { MemberEmail } from '../../domain/group/member-email';

import { Injectable } from '@angular/core';
import { catchError, firstValueFrom, map } from 'rxjs';

import { GroupMemberInvitationPort } from '../../application/group/group-member-invitation.port';
import { GroupsService } from '../api/generated';
import { toApiClientError } from '../api/api-client.error';

@Injectable({ providedIn: 'root' })
export class HttpGroupMemberInvitationGateway extends GroupMemberInvitationPort {
  constructor(private readonly groupsService: GroupsService) {
    super();
  }

  override invite(groupId: string, email: MemberEmail): Promise<void> {
    return firstValueFrom(
      this.groupsService.invite(groupId, { invitedMember: email }).pipe(
        catchError((error: unknown) => {
          throw toApiClientError(error, 'errors.members.invite');
        }),
        map(() => undefined),
      ),
    );
  }
}
