import { Component, input } from '@angular/core';

import type { ReimbursementSupportingDocumentViewItem } from './reimbursement-history-page.view-model';

@Component({
  selector: 'app-reimbursement-supporting-documents',
  templateUrl: './reimbursement-supporting-documents.component.html',
  styleUrl: './reimbursement-supporting-documents.component.scss',
})
export class ReimbursementSupportingDocumentsComponent {
  readonly documents = input.required<readonly ReimbursementSupportingDocumentViewItem[]>();
}
