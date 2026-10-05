import { Component, OnInit, input } from '@angular/core';
import { FormField, FormRoot } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';

import { SupportingDocumentUploadWidgetComponent } from '../../shared/supporting-document-upload/supporting-document-upload-widget.component';
import { DocumentedReimbursementWidgetViewModel } from './documented-reimbursement-widget.view-model';

@Component({
  selector: 'app-documented-reimbursement-widget',
  imports: [
    FormField,
    FormRoot,
    MatButtonModule,
    MatCardModule,
    SupportingDocumentUploadWidgetComponent,
  ],
  providers: [DocumentedReimbursementWidgetViewModel],
  templateUrl: './documented-reimbursement-widget.component.html',
  styleUrl: './documented-reimbursement-widget.component.scss',
})
export class DocumentedReimbursementWidgetComponent implements OnInit {
  readonly groupId = input.required<string>();

  constructor(readonly viewModel: DocumentedReimbursementWidgetViewModel) {}

  ngOnInit(): void {
    this.viewModel.initialize(this.groupId());
  }
}
