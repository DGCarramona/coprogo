import { Component, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatToolbarModule } from '@angular/material/toolbar';

import { InvitationCardComponent } from './invitation-card.component';
import { PendingInvitationsPageViewModel } from './pending-invitations-page.view-model';

@Component({
  selector: 'app-pending-invitations-page',
  imports: [InvitationCardComponent, MatButtonModule, MatCardModule, MatProgressBarModule, MatToolbarModule],
  templateUrl: './pending-invitations-page.component.html',
  styleUrl: './pending-invitations-page.component.scss',
  providers: [PendingInvitationsPageViewModel],
})
export class PendingInvitationsPageComponent implements OnInit {
  protected readonly viewModel = inject(PendingInvitationsPageViewModel);

  ngOnInit(): void {
    void this.viewModel.initialize();
  }
}
