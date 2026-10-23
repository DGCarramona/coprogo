import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { RouterLink } from '@angular/router';

import { ExpenseListWidgetComponent } from '../dashboard/expense-list-widget/expense-list-widget.component';
import { AuthenticatedShellComponent } from '../shared/authenticated-shell/authenticated-shell.component';
import { ExpenseListPageViewModel } from './expense-list-page.view-model';

@Component({
  selector: 'app-expense-list-page',
  imports: [
    AuthenticatedShellComponent,
    ExpenseListWidgetComponent,
    MatButtonModule,
    MatCardModule,
    RouterLink,
  ],
  templateUrl: './expense-list-page.component.html',
  styleUrl: './expense-list-page.component.scss',
  providers: [ExpenseListPageViewModel],
})
export class ExpenseListPageComponent {
  constructor(protected readonly viewModel: ExpenseListPageViewModel) {}
}
