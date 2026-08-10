import { Injectable } from '@angular/core';
import { catchError, firstValueFrom } from 'rxjs';

import { PendingGroupInvitationsPort } from '../../application/group/pending-group-invitations.port';
import type { PendingGroupInvitation } from '../../domain/group/pending-group-invitation';
import { mapArray } from '../../shared/rxjs/map-array';
import { GroupsService } from '../api/generated';
import { toApiClientError } from '../api/api-client.error';

@Injectable({ providedIn: 'root' })
export class HttpGroupInvitationsGateway extends PendingGroupInvitationsPort {
  constructor(private readonly groupsService: GroupsService) {
    super();
  }

  override async listPending(): Promise<PendingGroupInvitation[]> {
    return await firstValueFrom(
      this.groupsService.listPending().pipe(
        catchError((error) => {
          throw toApiClientError(error, 'Les invitations en attente n ont pas pu etre chargees.');
        }),
        mapArray(
          (invitation): PendingGroupInvitation => ({
            invitationId: invitation.invitation,
            groupId: invitation.group,
            invitedMember: invitation.invitedMember,
            invitedBy: invitation.invitedBy,
            invitedAt: new Date(invitation.invitedAt),
          }),
        ),
      ),
    );
  }

  override async accept(invitationId: string): Promise<void> {
    await firstValueFrom(
      this.groupsService.accept(invitationId).pipe(
        catchError((error) => {
          throw toApiClientError(error, "L'invitation n'a pas pu etre acceptee.");
        }),
      ),
    );
  }
}
