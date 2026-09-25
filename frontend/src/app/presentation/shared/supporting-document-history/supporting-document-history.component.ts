import { DatePipe } from '@angular/common';
import { Component, input } from '@angular/core';

import { SupportingDocumentHistoryViewModel } from './supporting-document-history.view-model';

export interface SupportingDocumentDeletionAudit {
  readonly deletedBy: string;
  readonly deletedAt: Date;
}

/**
 * Direct, short-lived URL to consult one historical document version.
 * It is intentionally kept out of visible text.
 */
export interface SupportingDocumentDownloadTarget {
  readonly url: string;
  readonly expiresAt: Date;
}

/**
 * One version in a single justificatif history, ordered from oldest to newest.
 * `sourceUploadIntent` is a stable reference for future actions and is never rendered.
 */
export interface SupportingDocumentVersion {
  readonly sourceUploadIntent: string;
  readonly fileName: string;
  readonly uploader: string;
  readonly attachedAt: Date;
  readonly downloadTarget: SupportingDocumentDownloadTarget;
  readonly replacesSourceUploadIntent: string | null;
  readonly deletion: SupportingDocumentDeletionAudit | null;
  readonly canDelete: boolean;
}

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
