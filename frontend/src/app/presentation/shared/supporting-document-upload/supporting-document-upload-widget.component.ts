import { Component, Input, output } from '@angular/core';

import type { SupportingDocumentFile } from '../../../application/supporting-document/supporting-document-upload.port';
import type { SupportingDocumentUploadResult } from './supporting-document-upload-result';
import { SupportingDocumentUploadWidgetViewModel } from './supporting-document-upload-widget.view-model';

@Component({
  selector: 'app-supporting-document-upload-widget',
  standalone: true,
  providers: [SupportingDocumentUploadWidgetViewModel],
  templateUrl: './supporting-document-upload-widget.component.html',
})
export class SupportingDocumentUploadWidgetComponent {
  readonly confirmed = output<SupportingDocumentUploadResult>();
  readonly uploadPendingChange = output<boolean>();

  @Input({ required: true })
  set groupId(groupId: string) {
    this.viewModel.initialize(groupId);
  }

  constructor(readonly viewModel: SupportingDocumentUploadWidgetViewModel) {}

  onFileSelection(event: Event): void {
    const input = event.target;
    if (!(input instanceof HTMLInputElement)) return;

    const file = input.files?.[0];
    if (file === null || file === undefined) return;

    this.uploadPendingChange.emit(true);
    void this.toSupportingDocumentFile(file)
      .then((supportingDocument) => this.viewModel.upload(supportingDocument))
      .then((intentId) => this.confirmed.emit({ intentId, fileName: file.name }))
      .catch(() => undefined)
      .finally(() => {
        this.uploadPendingChange.emit(false);
        input.value = '';
      });
  }

  private async toSupportingDocumentFile(file: File): Promise<SupportingDocumentFile> {
    return {
      fileName: file.name,
      mediaType: file.type || 'application/octet-stream',
      bytes: new Uint8Array(await file.arrayBuffer()),
    };
  }
}
