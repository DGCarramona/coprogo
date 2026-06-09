import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { PendingGroupInvitationsPort } from '../../application/group/pending-group-invitations.port';
import { PendingGroupInvitation } from '../../domain/group/pending-group-invitation';
import { API_BASE_PATH } from '../api/provide-api-client';
import { toApiClientError } from '../api/api-client.error';

interface PendingGroupInvitationDto {
  invitation: string;
  group: string;
  invitedMember: string;
  invitedBy: string;
  invitedAt: string;
}

@Injectable({ providedIn: 'root' })
export class HttpGroupInvitationsGateway extends PendingGroupInvitationsPort {
  private readonly httpClient = inject(HttpClient);
  private readonly basePath = inject(API_BASE_PATH);

  override async listPending(): Promise<PendingGroupInvitation[]> {
    try {
      const invitations = await firstValueFrom(
        this.httpClient.get<PendingGroupInvitationDto[]>(
          `${this.basePath}/api/group-invitations/pending`,
        ),
      );

      return invitations.map(mapPendingGroupInvitationDtoToDomain);
    } catch (error) {
      throw toApiClientError(error, 'Les invitations en attente n ont pas pu etre chargees.');
    }
  }

  override async accept(invitationId: string): Promise<void> {
    try {
      await firstValueFrom(
        this.httpClient.post<void>(
          `${this.basePath}/api/group-invitations/${invitationId}/accept`,
          {},
        ),
      );
    } catch (error) {
      throw toApiClientError(error, "L'invitation n'a pas pu etre acceptee.");
    }
  }
}

const mapPendingGroupInvitationDtoToDomain = (
  invitation: PendingGroupInvitationDto,
): PendingGroupInvitation => ({
  invitationId: invitation.invitation,
  groupId: invitation.group,
  invitedMember: invitation.invitedMember,
  invitedBy: invitation.invitedBy,
  invitedAt: new Date(invitation.invitedAt),
});
