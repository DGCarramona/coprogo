import { Component, Input, OnInit } from '@angular/core';
import { FormField, FormRoot } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';

import { SupportingDocumentUploadWidgetComponent } from '../../shared/supporting-document-upload/supporting-document-upload-widget.component';
import type { SupportingDocumentUploadResult } from '../../shared/supporting-document-upload/supporting-document-upload-result';
import { ExpenseProposalWidgetViewModel } from './expense-proposal-widget.view-model';

@Component({
  selector: 'app-expense-proposal-widget',
  standalone: true,
  imports: [
    FormField,
    FormRoot,
    MatButtonModule,
    MatCardModule,
    SupportingDocumentUploadWidgetComponent,
  ],
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

  updateEqualWithCapsMaximum(member: string, event: Event): void {
    const input = event.target;
    if (!(input instanceof HTMLInputElement)) return;
    this.viewModel.setEqualWithCapsMaximum(member, input.value);
  }

  updateCumulativeIntermediateThreshold(index: number, event: Event): void {
    const input = event.target;
    if (!(input instanceof HTMLInputElement)) return;
    this.viewModel.setCumulativeIntermediateThreshold(index, input.value);
  }

  updateCustomAmount(member: string, event: Event): void {
    const input = event.target;
    if (!(input instanceof HTMLInputElement)) return;
    this.viewModel.setCustomAmount(member, input.value);
  }

  addSupportingDocument(document: SupportingDocumentUploadResult): void {
    this.viewModel.addSupportingDocument(document);
  }
}
