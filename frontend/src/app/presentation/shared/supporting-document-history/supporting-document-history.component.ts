import { DatePipe } from '@angular/common';
import { Component, input } from '@angular/core';

import type { ExpenseSupportingDocument } from '../../../application/supporting-document/expense-supporting-documents.port';
import { SupportingDocumentHistoryViewModel } from './supporting-document-history.view-model';

export type SupportingDocumentVersion = ExpenseSupportingDocument;

@Component({
  selector: 'app-supporting-document-history',
  standalone: true,
  imports: [DatePipe],
  providers: [SupportingDocumentHistoryViewModel],
  templateUrl: './supporting-document-history.component.html',
})
export class SupportingDocumentHistoryComponent {
  readonly versions = input.required<readonly SupportingDocumentVersion[]>();
  readonly groupId = input.required<string>();
  readonly expenseId = input.required<string>();

  constructor(readonly viewModel: SupportingDocumentHistoryViewModel) {}

  canRemove(version: SupportingDocumentVersion): boolean {
    return (
      this.versions().at(-1)?.sourceUploadIntent === version.sourceUploadIntent &&
      version.deletion === null &&
      version.canDelete &&
      !this.viewModel.wasDeleted(version.sourceUploadIntent)
    );
  }

  remove(version: SupportingDocumentVersion): void {
    void this.viewModel
      .delete({
        groupId: this.groupId(),
        expenseId: this.expenseId(),
        sourceUploadIntent: version.sourceUploadIntent,
      })
      .catch(() => undefined);
  }
}
