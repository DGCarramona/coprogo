import { DatePipe } from '@angular/common';
import { Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';

import { PendingGroupInvitation } from '../../domain/group/pending-group-invitation';

@Component({
  selector: 'app-invitation-card',
  imports: [DatePipe, MatButtonModule, MatCardModule, MatChipsModule],
  templateUrl: './invitation-card.component.html',
  styleUrl: './invitation-card.component.scss',
})
export class InvitationCardComponent {
  readonly invitation = input.required<PendingGroupInvitation>();
  readonly accepting = input(false);
  readonly accepted = output<string>();
  protected readonly shortGroupId = computed(() => this.invitation().groupId.slice(0, 8).toUpperCase());

  protected accept(): void {
    this.accepted.emit(this.invitation().invitationId);
  }
}
