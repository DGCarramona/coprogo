import { Component, OnInit } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { AuthenticatedShellComponent } from '../shared/authenticated-shell/authenticated-shell.component';
import { CreateFirstGroupCardComponent } from './create-first-group-card.component';
import { CreateFirstGroupViewModel } from './create-first-group.view-model';
import { PendingInvitationsListComponent } from './pending-invitations-list.component';
import { PendingInvitationsViewModel } from './pending-invitations.view-model';

@Component({
  selector: 'app-pending-invitations-page',
  imports: [
    AuthenticatedShellComponent,
    CreateFirstGroupCardComponent,
    PendingInvitationsListComponent,
    MatCardModule,
    MatProgressBarModule,
  ],
  templateUrl: './pending-invitations-page.component.html',
  styleUrl: './pending-invitations-page.component.scss',
  providers: [PendingInvitationsViewModel, CreateFirstGroupViewModel],
})
export class PendingInvitationsPageComponent implements OnInit {
  constructor(
    protected readonly invitationsViewModel: PendingInvitationsViewModel,
    protected readonly createGroupViewModel: CreateFirstGroupViewModel,
  ) {}

  ngOnInit(): void {
    void this.invitationsViewModel.initialize();
  }
}
