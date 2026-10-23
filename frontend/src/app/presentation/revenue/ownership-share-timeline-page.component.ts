import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';

import { AuthenticatedShellComponent } from '../shared/authenticated-shell/authenticated-shell.component';
import { OwnershipShareChangeWidgetComponent } from './ownership-share-change-widget.component';
import { OwnershipShareTimelinePageViewModel } from './ownership-share-timeline-page.view-model';

@Component({
  selector: 'app-ownership-share-timeline-page',
  imports: [
    AuthenticatedShellComponent,
    MatButtonModule,
    MatCardModule,
    MatProgressBarModule,
    OwnershipShareChangeWidgetComponent,
    RouterLink,
  ],
  templateUrl: './ownership-share-timeline-page.component.html',
  styleUrl: './ownership-share-timeline-page.component.scss',
  providers: [OwnershipShareTimelinePageViewModel],
})
export class OwnershipShareTimelinePageComponent {
  constructor(protected readonly viewModel: OwnershipShareTimelinePageViewModel) {}
}
