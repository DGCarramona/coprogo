import { Component, Input, OnInit } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { ExpenseListWidgetViewModel } from './expense-list-widget.view-model';

@Component({
  selector: 'app-expense-list-widget',
  imports: [MatCardModule, MatProgressBarModule, MatButtonModule],
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
