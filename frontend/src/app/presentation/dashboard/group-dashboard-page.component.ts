import { Component, OnInit } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';

import { AuthenticatedShellComponent } from '../shared/authenticated-shell/authenticated-shell.component';
import { CashPoolHistoryWidgetComponent } from './cash-pool-history-widget/cash-pool-history-widget.component';
import { CashPoolIncomeWidgetComponent } from './cash-pool-income-widget/cash-pool-income-widget.component';
import { CashPoolWithdrawalWidgetComponent } from './cash-pool-withdrawal-widget/cash-pool-withdrawal-widget.component';
import { ExpenseListWidgetComponent } from './expense-list-widget/expense-list-widget.component';
import { ExpenseProposalWidgetComponent } from './expense-proposal-widget/expense-proposal-widget.component';
import { GroupDashboardPageViewModel } from './group-dashboard-page.view-model';
import { MemberDebtReductionHistoryWidgetComponent } from './member-debt-reduction-history-widget/member-debt-reduction-history-widget.component';

@Component({
  selector: 'app-group-dashboard-page',
  imports: [
    AuthenticatedShellComponent,
    CashPoolHistoryWidgetComponent,
    CashPoolIncomeWidgetComponent,
    CashPoolWithdrawalWidgetComponent,
    ExpenseListWidgetComponent,
    ExpenseProposalWidgetComponent,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatProgressBarModule,
    MemberDebtReductionHistoryWidgetComponent,
    RouterLink,
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
