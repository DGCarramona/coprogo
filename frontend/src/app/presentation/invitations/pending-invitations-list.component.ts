import { Component, input, output } from '@angular/core';

import { PendingGroupInvitation } from '../../domain/group/pending-group-invitation';
import { InvitationCardComponent } from './invitation-card.component';

@Component({
  selector: 'app-pending-invitations-list',
  imports: [InvitationCardComponent],
  templateUrl: './pending-invitations-list.component.html',
})
export class PendingInvitationsListComponent {
  readonly invitations = input<readonly PendingGroupInvitation[]>([]);
  readonly busyInvitationId = input<string | null>(null);
  readonly errorMessage = input<string | null>(null);
  readonly invitationAccepted = output<string>();
}
