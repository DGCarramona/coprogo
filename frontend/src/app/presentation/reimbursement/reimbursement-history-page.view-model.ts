import { computed, Injectable, Injector } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { injectQuery } from '@tanstack/angular-query-experimental';

import { ReimbursementListPort } from '../../application/reimbursement/reimbursement-list.port';
import { describeError } from '../../application/shared/describe-error';
import type { ReimbursementSummary } from '../../domain/reimbursement/reimbursement-summary';
import { FinancialFormatter } from '../i18n/financial-formatter';

export interface ReimbursementHistoryViewItem {
  readonly id: string;
  readonly paidBy: string;
  readonly receivedBy: string;
  readonly amount: string;
  readonly reimbursedAt: string;
  readonly awaitsReview: boolean;
  readonly reviewStatusLabel: string | null;
  readonly rejectionReason: string | null;
  readonly documents: readonly ReimbursementSupportingDocumentViewItem[];
}

export interface ReimbursementSupportingDocumentViewItem {
  readonly sourceUploadIntent: string;
  readonly fileName: string;
  readonly uploader: string;
  readonly attachedAt: string;
  readonly downloadUrl: string;
}

@Injectable()
export class ReimbursementHistoryPageViewModel {
  readonly groupId: string | null;
  private readonly reimbursementsQuery;

  readonly reimbursements = computed<readonly ReimbursementHistoryViewItem[]>(
    () =>
      this.reimbursementsQuery
        .data()
        ?.map((reimbursement) => toViewItem(reimbursement, this.formatter)) ?? [],
  );
  readonly isLoading = computed(
    () => this.groupId !== null && this.reimbursementsQuery.isLoading(),
  );
  readonly hasLoadError = computed(
    () => this.groupId === null || this.reimbursementsQuery.isError(),
  );
  readonly isReady = computed(() => this.groupId !== null && this.reimbursementsQuery.isSuccess());
  readonly errorMessage = computed(() => {
    if (this.groupId === null) return 'errors.groups.noneSelected';

    const error = this.reimbursementsQuery.error();
    return error === null ? null : describeError(error, 'errors.reimbursements.loadHistory');
  });

  constructor(
    route: ActivatedRoute,
    private readonly reimbursementPort: ReimbursementListPort,
    injector: Injector,
    private readonly formatter: FinancialFormatter,
  ) {
    this.groupId = route.snapshot.paramMap.get('groupId');
    this.reimbursementsQuery = injectQuery(
      () => ({
        queryKey: ['groups', this.groupId, 'reimbursements'] as const,
        queryFn: (): Promise<readonly ReimbursementSummary[]> => {
          if (this.groupId === null) {
            throw new Error('errors.reimbursements.missingHistoryGroup');
          }

          return this.reimbursementPort.listByGroup(this.groupId);
        },
        enabled: this.groupId !== null,
        staleTime: 30_000,
      }),
      { injector },
    );
  }

  retry(): void {
    void this.reimbursementsQuery.refetch();
  }
}

const toViewItem = (
  reimbursement: ReimbursementSummary,
  formatter: FinancialFormatter,
): ReimbursementHistoryViewItem => ({
  id: reimbursement.id,
  paidBy: reimbursement.paidBy,
  receivedBy: reimbursement.receivedBy,
  amount: formatter.money(reimbursement.amountInCents),
  reimbursedAt: formatter.date(reimbursement.reimbursedAt),
  awaitsReview: reimbursement.status === 'PENDING_REVIEW',
  reviewStatusLabel: toReviewStatusLabel(reimbursement),
  rejectionReason: reimbursement.status === 'REJECTED' ? reimbursement.rejectionReason : null,
  documents: reimbursement.documents.map((document) => ({
    sourceUploadIntent: document.sourceUploadIntent,
    fileName: document.fileName,
    uploader: document.uploader,
    attachedAt: formatter.date(document.attachedAt),
    downloadUrl: document.downloadTarget.url,
  })),
});

const toReviewStatusLabel = (reimbursement: ReimbursementSummary): string | null => {
  if (reimbursement.documents.length === 0) return null;

  switch (reimbursement.status) {
    case 'PENDING_REVIEW':
      return 'statuses.pendingReview';
    case 'ACCEPTED':
      return 'statuses.accepted';
    case 'REJECTED':
      return 'statuses.rejected';
  }
};
