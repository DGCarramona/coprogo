import { Component, OnInit, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';

import { ReimbursementReviewViewModel } from './reimbursement-review.view-model';

@Component({
  selector: 'app-reimbursement-review',
  imports: [MatButtonModule],
  providers: [ReimbursementReviewViewModel],
  templateUrl: './reimbursement-review.component.html',
  styleUrl: './reimbursement-review.component.scss',
})
export class ReimbursementReviewComponent implements OnInit {
  readonly groupId = input.required<string>();
  readonly reimbursementId = input.required<string>();

  constructor(readonly viewModel: ReimbursementReviewViewModel) {}

  ngOnInit(): void {
    this.viewModel.initialize(this.groupId(), this.reimbursementId());
  }

  updateRejectionReason(event: Event): void {
    const textarea = event.target;
    if (!(textarea instanceof HTMLTextAreaElement)) return;
    this.viewModel.rejectionReason.set(textarea.value);
  }
}
