import { Component, OnInit, input } from '@angular/core';
import { FormField, FormRoot } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';

import { DirectReimbursementWidgetViewModel } from './direct-reimbursement-widget.view-model';

@Component({
  selector: 'app-direct-reimbursement-widget',
  imports: [FormField, FormRoot, MatButtonModule, MatCardModule],
  providers: [DirectReimbursementWidgetViewModel],
  templateUrl: './direct-reimbursement-widget.component.html',
  styleUrl: './direct-reimbursement-widget.component.scss',
})
export class DirectReimbursementWidgetComponent implements OnInit {
  readonly groupId = input.required<string>();

  constructor(readonly viewModel: DirectReimbursementWidgetViewModel) {}

  ngOnInit(): void {
    this.viewModel.initialize(this.groupId());
  }
}
