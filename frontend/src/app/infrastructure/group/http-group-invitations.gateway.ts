import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { PendingGroupInvitationsPort } from '../../application/group/pending-group-invitations.port';
import { PendingGroupInvitation } from '../../domain/group/pending-group-invitation';
import { GroupsService, PendingGroupInvitationResponseDto } from '../api/generated';
import { toApiClientError } from '../api/api-client.error';

@Injectable({ providedIn: 'root' })
export class HttpGroupInvitationsGateway extends PendingGroupInvitationsPort {
  constructor(private readonly groupsService: GroupsService) {
    super();
  }

  override async listPending(): Promise<PendingGroupInvitation[]> {
    try {
      const invitations = await firstValueFrom(this.groupsService.listPending());
      return invitations.map(mapPendingGroupInvitationDtoToDomain);
    } catch (error) {
      throw toApiClientError(error, 'Les invitations en attente n ont pas pu etre chargees.');
    }
  }

  override async accept(invitationId: string): Promise<void> {
    try {
      await firstValueFrom(this.groupsService.accept(invitationId));
    } catch (error) {
      throw toApiClientError(error, "L'invitation n'a pas pu etre acceptee.");
    }
  }
}

const mapPendingGroupInvitationDtoToDomain = (
  invitation: PendingGroupInvitationResponseDto,
): PendingGroupInvitation => ({
  invitationId: invitation.invitation,
  groupId: invitation.group,
  invitedMember: invitation.invitedMember,
  invitedBy: invitation.invitedBy,
  invitedAt: new Date(invitation.invitedAt),
});
