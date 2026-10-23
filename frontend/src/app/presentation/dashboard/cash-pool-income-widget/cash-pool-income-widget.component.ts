import { Component, effect, input, OnInit, output } from '@angular/core';
import { FormField, FormRoot } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';

import { CashPoolIncomeWidgetViewModel } from './cash-pool-income-widget.view-model';

@Component({
  selector: 'app-cash-pool-income-widget',
  imports: [FormField, FormRoot, MatButtonModule, MatCardModule],
  providers: [CashPoolIncomeWidgetViewModel],
  templateUrl: './cash-pool-income-widget.component.html',
  styleUrl: './cash-pool-income-widget.component.scss',
})
export class CashPoolIncomeWidgetComponent implements OnInit {
  readonly groupId = input.required<string>();
  readonly recorded = output<void>();

  constructor(readonly viewModel: CashPoolIncomeWidgetViewModel) {
    effect(() => {
      if (this.viewModel.isRecorded()) this.recorded.emit();
    });
  }

  ngOnInit(): void {
    this.viewModel.initialize(this.groupId());
  }
}
