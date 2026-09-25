import { DatePipe } from '@angular/common';
import { Component, input } from '@angular/core';

export interface SupportingDocumentDeletionAudit {
  readonly deletedBy: string;
  readonly deletedAt: Date;
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
  readonly replacesSourceUploadIntent: string | null;
  readonly deletion: SupportingDocumentDeletionAudit | null;
}

@Component({
  selector: 'app-supporting-document-history',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './supporting-document-history.component.html',
})
export class SupportingDocumentHistoryComponent {
  readonly versions = input.required<readonly SupportingDocumentVersion[]>();
}
