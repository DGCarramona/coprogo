import { Injectable } from '@angular/core';
import { catchError, firstValueFrom, map } from 'rxjs';
import {
  GroupInvitationsPort,
  SentGroupInvitation,
} from '../../application/group/group-invitations.port';
import { GroupsService } from '../api/generated';
import { toApiClientError } from '../api/api-client.error';

@Injectable({ providedIn: 'root' })
export class HttpGroupInvitationsManagementGateway extends GroupInvitationsPort {
  constructor(private readonly groups: GroupsService) {
    super();
  }

  override list(groupId: string): Promise<readonly SentGroupInvitation[]> {
    return firstValueFrom(
      this.groups.get(groupId).pipe(
        map((group) =>
          group.pendingInvitations.map((invitation) => ({
            ...invitation,
            invitedAt: new Date(invitation.invitedAt),
            canCancel: invitation.canCancel ?? false,
          })),
        ),
        catchError((error: unknown) => {
          throw toApiClientError(error, 'errors.members.loadInvitations');
        }),
      ),
    );
  }

  override cancel(groupId: string, invitationId: string): Promise<void> {
    return firstValueFrom(
      this.groups.cancelGroupInvitation(groupId, invitationId).pipe(
        map(() => undefined),
        catchError((error: unknown) => {
          throw toApiClientError(error, 'errors.members.cancelInvitation');
        }),
      ),
    );
  }
}
