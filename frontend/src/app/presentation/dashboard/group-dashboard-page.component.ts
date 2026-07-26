import { Component, OnInit } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { AuthenticatedShellComponent } from '../shared/authenticated-shell/authenticated-shell.component';
import { GroupDashboardPageViewModel } from './group-dashboard-page.view-model';

@Component({
  selector: 'app-group-dashboard-page',
  imports: [
    AuthenticatedShellComponent,
    MatCardModule,
    MatChipsModule,
    MatProgressBarModule,
  ],
  templateUrl: './group-dashboard-page.component.html',
  styleUrl: './group-dashboard-page.component.scss',
  providers: [GroupDashboardPageViewModel],
})
export class GroupDashboardPageComponent implements OnInit {
  constructor(protected readonly viewModel: GroupDashboardPageViewModel) {}

  ngOnInit(): void {
    void this.viewModel.initialize();
  }
}
