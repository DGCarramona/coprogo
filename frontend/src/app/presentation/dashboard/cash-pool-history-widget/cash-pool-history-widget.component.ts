import { Component, input, OnInit } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { CashPoolHistoryWidgetViewModel } from './cash-pool-history-widget.view-model';

@Component({
  selector: 'app-cash-pool-history-widget',
  imports: [MatButtonModule, MatCardModule, MatProgressBarModule],
  providers: [CashPoolHistoryWidgetViewModel],
  templateUrl: './cash-pool-history-widget.component.html',
  styleUrl: './cash-pool-history-widget.component.scss',
})
export class CashPoolHistoryWidgetComponent implements OnInit {
  readonly groupId = input.required<string>();

  constructor(readonly viewModel: CashPoolHistoryWidgetViewModel) {}

  ngOnInit(): void {
    this.viewModel.load(this.groupId());
  }
}
