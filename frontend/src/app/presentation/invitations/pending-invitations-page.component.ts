import { Component, OnInit } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatToolbarModule } from '@angular/material/toolbar';

import { CreateFirstGroupCardComponent } from './create-first-group-card.component';
import { CreateFirstGroupViewModel } from './create-first-group.view-model';
import { PendingInvitationsListComponent } from './pending-invitations-list.component';
import { PendingInvitationsViewModel } from './pending-invitations.view-model';

@Component({
  selector: 'app-pending-invitations-page',
  imports: [
    CreateFirstGroupCardComponent,
    PendingInvitationsListComponent,
    MatButtonModule,
    MatCardModule,
    MatProgressBarModule,
    MatToolbarModule,
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
