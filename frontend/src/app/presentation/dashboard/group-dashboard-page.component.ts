import { Component, OnInit } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatToolbarModule } from '@angular/material/toolbar';

import { GroupDashboardPageViewModel } from './group-dashboard-page.view-model';

@Component({
  selector: 'app-group-dashboard-page',
  imports: [
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatDividerModule,
    MatProgressBarModule,
    MatToolbarModule,
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
