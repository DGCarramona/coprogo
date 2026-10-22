import { Component, effect, input, OnInit, output } from '@angular/core';
import { FormField, FormRoot } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';

import { CashPoolWithdrawalWidgetViewModel } from './cash-pool-withdrawal-widget.view-model';

@Component({
  selector: 'app-cash-pool-withdrawal-widget',
  imports: [FormField, FormRoot, MatButtonModule, MatCardModule],
  providers: [CashPoolWithdrawalWidgetViewModel],
  templateUrl: './cash-pool-withdrawal-widget.component.html',
  styleUrl: './cash-pool-withdrawal-widget.component.scss',
})
export class CashPoolWithdrawalWidgetComponent implements OnInit {
  readonly groupId = input.required<string>();
  readonly withdrawn = output<void>();

  constructor(readonly viewModel: CashPoolWithdrawalWidgetViewModel) {
    effect(() => {
      if (this.viewModel.isWithdrawn()) this.withdrawn.emit();
    });
  }

  ngOnInit(): void {
    this.viewModel.initialize(this.groupId());
  }
}
