import { Component, OnInit, input } from '@angular/core';

import { SupportingDocumentHistoryComponent } from '../supporting-document-history/supporting-document-history.component';
import { ExpenseSupportingDocumentsWidgetViewModel } from './expense-supporting-documents-widget.view-model';

@Component({
  selector: 'app-expense-supporting-documents-widget',
  standalone: true,
  imports: [SupportingDocumentHistoryComponent],
  providers: [ExpenseSupportingDocumentsWidgetViewModel],
  templateUrl: './expense-supporting-documents-widget.component.html',
})
export class ExpenseSupportingDocumentsWidgetComponent implements OnInit {
  readonly groupId = input.required<string>();
  readonly expenseId = input.required<string>();

  constructor(protected readonly viewModel: ExpenseSupportingDocumentsWidgetViewModel) {}

  ngOnInit(): void {
    this.viewModel.initialize(this.groupId(), this.expenseId());
  }
}
