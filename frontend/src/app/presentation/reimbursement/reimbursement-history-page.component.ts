import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';

import { AuthenticatedShellComponent } from '../shared/authenticated-shell/authenticated-shell.component';
import { DocumentedReimbursementWidgetComponent } from './documented-reimbursement-widget/documented-reimbursement-widget.component';
import { DirectReimbursementWidgetComponent } from './direct-reimbursement-widget/direct-reimbursement-widget.component';
import { ReimbursementHistoryPageViewModel } from './reimbursement-history-page.view-model';
import { ReimbursementReviewComponent } from './reimbursement-review/reimbursement-review.component';
import { ReimbursementSupportingDocumentsComponent } from './reimbursement-supporting-documents.component';

@Component({
  selector: 'app-reimbursement-history-page',
  imports: [
    AuthenticatedShellComponent,
    DocumentedReimbursementWidgetComponent,
    DirectReimbursementWidgetComponent,
    MatButtonModule,
    MatCardModule,
    MatProgressBarModule,
    ReimbursementReviewComponent,
    ReimbursementSupportingDocumentsComponent,
    RouterLink,
  ],
  templateUrl: './reimbursement-history-page.component.html',
  styleUrl: './reimbursement-history-page.component.scss',
  providers: [ReimbursementHistoryPageViewModel],
})
export class ReimbursementHistoryPageComponent {
  constructor(protected readonly viewModel: ReimbursementHistoryPageViewModel) {}
}
