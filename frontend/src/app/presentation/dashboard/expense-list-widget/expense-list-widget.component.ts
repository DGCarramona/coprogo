import { Component, Input, OnInit } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { ExpenseListWidgetViewModel } from './expense-list-widget.view-model';
import { ExpenseSupportingDocumentsWidgetComponent } from '../../shared/expense-supporting-documents-widget/expense-supporting-documents-widget.component';

@Component({
  selector: 'app-expense-list-widget',
  imports: [
    MatCardModule,
    MatProgressBarModule,
    MatButtonModule,
    ExpenseSupportingDocumentsWidgetComponent,
  ],
  templateUrl: './expense-list-widget.component.html',
  styleUrl: './expense-list-widget.component.scss',
  providers: [ExpenseListWidgetViewModel],
})
export class ExpenseListWidgetComponent implements OnInit {
  @Input() groupId!: string;

  constructor(protected readonly viewModel: ExpenseListWidgetViewModel) {}

  ngOnInit(): void {
    void this.viewModel.initialize(this.groupId);
  }
}
