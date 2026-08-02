import { Component, Input, OnInit } from '@angular/core';
import { FormField, FormRoot } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';

import { ExpenseProposalWidgetViewModel } from './expense-proposal-widget.view-model';

@Component({
  selector: 'app-expense-proposal-widget',
  standalone: true,
  imports: [FormField, FormRoot, MatButtonModule, MatCardModule],
  providers: [ExpenseProposalWidgetViewModel],
  templateUrl: './expense-proposal-widget.component.html',
  styleUrl: './expense-proposal-widget.component.scss',
})
export class ExpenseProposalWidgetComponent implements OnInit {
  @Input({ required: true }) groupId!: string;

  constructor(readonly viewModel: ExpenseProposalWidgetViewModel) {}

  ngOnInit(): void {
    this.viewModel.initialize(this.groupId);
  }
}
